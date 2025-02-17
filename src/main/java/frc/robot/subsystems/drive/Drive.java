package frc.robot.subsystems.drive;

import static edu.wpi.first.units.Units.*;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import com.pathplanner.lib.pathfinding.Pathfinding;
import com.pathplanner.lib.util.HolonomicPathFollowerConfig;
import com.pathplanner.lib.util.PathPlannerLogging;
import com.pathplanner.lib.util.ReplanningConfig;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Twist2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine;
import frc.robot.Constants.DriveConstants;
import frc.robot.bobot_state.BobotState;
import frc.robot.pathplanner.LocalADStarAK;
import frc.util.GeomUtils;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.littletonrobotics.junction.AutoLogOutput;
import org.littletonrobotics.junction.Logger;

public class Drive extends SubsystemBase {

  private static final double kLookaheadTimeSeconds = 0.20;

  private static final double MAX_LINEAR_SPEED = Units.feetToMeters(14.5);
  private static final double TRACK_WIDTH_X = Units.inchesToMeters(28.0);
  private static final double TRACK_WIDTH_Y = Units.inchesToMeters(28.0);
  private static final double DRIVE_BASE_RADIUS =
      Math.hypot(TRACK_WIDTH_X / 2.0, TRACK_WIDTH_Y / 2.0);
  private static final double MAX_ANGULAR_SPEED = MAX_LINEAR_SPEED / DRIVE_BASE_RADIUS;

  static final Lock odometryLock = new ReentrantLock();
  private final GyroIO gyroIO;
  private final GyroIOInputsAutoLogged gyroInputs = new GyroIOInputsAutoLogged();
  private final Module[] modules = new Module[4]; // FL, FR, BL, BR
  private final ModuleIOInputsAutoLogged[] m_moduleInputs =
      new ModuleIOInputsAutoLogged[] {
        new ModuleIOInputsAutoLogged(),
        new ModuleIOInputsAutoLogged(),
        new ModuleIOInputsAutoLogged(),
        new ModuleIOInputsAutoLogged(),
      };

  private final SysIdRoutine sysId;

  private SwerveDriveKinematics kinematics = new SwerveDriveKinematics(getModuleTranslations());
  // private Rotation2d rawGyroRotation = new Rotation2d();
  private SwerveModulePosition[] lastModulePositions = // For delta tracking
      new SwerveModulePosition[] {
        new SwerveModulePosition(),
        new SwerveModulePosition(),
        new SwerveModulePosition(),
        new SwerveModulePosition()
      };

  // private SwerveDrivePoseEstimator poseEstimator =
  //     new SwerveDrivePoseEstimator(kinematics, m_trackedRotation, lastModulePositions, new
  // Pose2d());

  // Adding 4451 Odo code
  private Rotation2d m_trackedRotation = new Rotation2d();
  private final SwerveDrivePoseEstimator m_combinedPoseEstimator =
      new SwerveDrivePoseEstimator(
          kinematics, m_trackedRotation, getModulePositions(), new Pose2d());

  private final SwerveDrivePoseEstimator m_visionOnlyPoseEstimator =
      new SwerveDrivePoseEstimator(
          kinematics, m_trackedRotation, getModulePositions(), new Pose2d());

  private final SwerveDrivePoseEstimator m_wheelOnlyPoseEstimator =
      new SwerveDrivePoseEstimator(
          kinematics, m_trackedRotation, getModulePositions(), new Pose2d());

  private final List<SwerveDrivePoseEstimator> m_poseEstimators =
      List.of(m_combinedPoseEstimator, m_visionOnlyPoseEstimator, m_wheelOnlyPoseEstimator);

  // private final Supplier<VisionMeasurement> m_visionMeasurementSupplier;

  public Drive(
      GyroIO gyroIO,
      ModuleIO flModuleIO,
      ModuleIO frModuleIO,
      ModuleIO blModuleIO,
      ModuleIO brModuleIO
      // Supplier<VisionMeasurement> visionMeasurementSupplier
      ) {
    this.gyroIO = gyroIO;
    modules[0] = new Module(flModuleIO, 3);
    modules[1] = new Module(frModuleIO, 2); // Change this order instead of re-instantiating
    modules[2] = new Module(blModuleIO, 1); // 6
    modules[3] = new Module(brModuleIO, 0); // 8

    // m_visionMeasurementSupplier = visionMeasurementSupplier;

    // Start threads (no-op for each if no signals have been created)
    PhoenixOdometryThread.getInstance().start();

    // Configure AutoBuilder for PathPlanner
    AutoBuilder.configureHolonomic(
        this::getPose,
        this::setPose,
        () -> kinematics.toChassisSpeeds(getModuleStates()),
        this::runVelocity,
        new HolonomicPathFollowerConfig(
            MAX_LINEAR_SPEED, DRIVE_BASE_RADIUS, new ReplanningConfig()),
        () ->
            DriverStation.getAlliance().isPresent()
                && DriverStation.getAlliance().get() == Alliance.Red,
        this);

    PPHolonomicDriveController.setRotationTargetOverride(BobotState::VARC);

    Pathfinding.setPathfinder(new LocalADStarAK());
    PathPlannerLogging.setLogActivePathCallback(
        (activePath) -> {
          Logger.recordOutput(
              "Odometry/Trajectory", activePath.toArray(new Pose2d[activePath.size()]));
        });
    PathPlannerLogging.setLogTargetPoseCallback(
        (targetPose) -> {
          Logger.recordOutput("Odometry/TrajectorySetpoint", targetPose);
        });

    // Configure SysId
    sysId =
        new SysIdRoutine(
            new SysIdRoutine.Config(
                null,
                null,
                null,
                (state) -> Logger.recordOutput("Drive/SysIdState", state.toString())),
            new SysIdRoutine.Mechanism(
                (voltage) -> {
                  for (int i = 0; i < 4; i++) {
                    modules[i].runCharacterization(voltage.in(Volts));
                  }
                },
                null,
                this));
  }

  // private void addVisionMeasurements() {
  //   // Pose2d currentPose = getPose();

  //   VisionMeasurement visionMeasurement;
  //   while ((visionMeasurement = m_visionMeasurementSupplier.get()) != null) {
  //     Pose2d visionPose = visionMeasurement.estimation().estimatedPose.toPose2d();
  //     // Ignore the vision pose's rotation
  //     // Pose2d visionPoseWithoutRotation = new Pose2d(visionPose.getTranslation(),
  //     // currentPose.getRotation());
  //     double timestampSeconds = visionMeasurement.estimation().timestampSeconds;
  //     var confidence = visionMeasurement.confidence();

  //     m_combinedPoseEstimator.addVisionMeasurement(visionPose, timestampSeconds, confidence);
  //     m_visionOnlyPoseEstimator.addVisionMeasurement(visionPose, timestampSeconds, confidence);
  //   }
  // }

  public SwerveModuleState[] getModuleStates() {

    SwerveModuleState[] states = new SwerveModuleState[modules.length];
    for (int i = 0; i < m_moduleInputs.length; i++) {
      states[i] = modules[i].getState();
    }
    return states;
  }

  public SwerveModulePosition[] getModulePositions() {
    SwerveModulePosition[] positions = new SwerveModulePosition[modules.length];
    for (int i = 0; i < m_moduleInputs.length; i++) {
      positions[i] = m_moduleInputs[i].position;
    }
    return positions;
  }

  @Override
  public void periodic() {
    SwerveModuleState[] states = getModuleStates();
    SwerveModulePosition[] positions = getModulePositions();

    Logger.recordOutput("SwereveStates/Measued", states);

    // odometryLock.lock(); // Prevents odometry updates while reading data
    gyroIO.updateInputs(gyroInputs);

    // odometryLock.unlock();
    Logger.processInputs("Drive/Gyro", gyroInputs);
    for (var module : modules) {
      module.periodic();
    }

    // Stop moving when disabled
    if (DriverStation.isDisabled()) {
      for (var module : modules) {
        module.stop();
      }
    }
    // Log empty setpoint states when disabled
    if (DriverStation.isDisabled()) {
      Logger.recordOutput("SwerveStates/Setpoints", new SwerveModuleState[] {});
      Logger.recordOutput("SwerveStates/SetpointsOptimized", new SwerveModuleState[] {});
    }

    // Update odometry
    double[] sampleTimestamps =
        modules[0].getOdometryTimestamps(); // All signals are sampled together
    int sampleCount = sampleTimestamps.length;
    for (int i = 0; i < sampleCount; i++) {
      // Read wheel positions and deltas from each module
      SwerveModulePosition[] modulePositions = new SwerveModulePosition[4];
      SwerveModulePosition[] moduleDeltas = new SwerveModulePosition[4];
      for (int moduleIndex = 0;
          moduleIndex < 4 && sampleCount == sampleTimestamps.length;
          moduleIndex++) {
        modulePositions[moduleIndex] = modules[moduleIndex].getOdometryPositions()[i];
        moduleDeltas[moduleIndex] =
            new SwerveModulePosition(
                modulePositions[moduleIndex].distanceMeters
                    - lastModulePositions[moduleIndex].distanceMeters,
                modulePositions[moduleIndex].angle);
        lastModulePositions[moduleIndex] = modulePositions[moduleIndex];
      }

      // Update gyro angle
      if (gyroInputs.connected) {
        // Use the real gyro angle
        m_trackedRotation = gyroInputs.yawPosition;
      } else {
        // Use the angle delta from the kinematics and module deltas
        Twist2d twist = kinematics.toTwist2d(moduleDeltas);
        m_trackedRotation = m_trackedRotation.plus(new Rotation2d(twist.dtheta));
      }

      m_combinedPoseEstimator.update(m_trackedRotation, positions);
      m_wheelOnlyPoseEstimator.update(m_trackedRotation, positions);
      // addVisionMeasurements();
      //
      Pose2d combinedPose = getPose();
      Pose2d visionOnlyPose = m_visionOnlyPoseEstimator.getEstimatedPosition();
      Pose2d wheelOnlyPose = m_wheelOnlyPoseEstimator.getEstimatedPosition();

      Pose2d predictedPose = getPredictedPose();

      BobotState.updateRobotPose(wheelOnlyPose);
      BobotState.updatePredictedPose(predictedPose);

      Logger.recordOutput("Drive/Velocity", getVelocitySpeeds());

      Logger.recordOutput("Odometry/Combined/Pose", combinedPose);
      Logger.recordOutput("Odometry/Combined/RotationDeg", combinedPose.getRotation().getDegrees());

      Logger.recordOutput("Odometry/VisionOnly/Pose", visionOnlyPose);
      Logger.recordOutput(
          "Odometry/VisionOnly/RotationDeg", visionOnlyPose.getRotation().getDegrees());

      Logger.recordOutput("Odometry/WheelOnly/Pose", wheelOnlyPose);
      Logger.recordOutput(
          "Odometry/WheelOnly/RotationDeg", wheelOnlyPose.getRotation().getDegrees());

      Logger.recordOutput("Odometry/Predicted/Pose", predictedPose);
      Logger.recordOutput(
          "Odometry/Predicted/RotationDeg", predictedPose.getRotation().getDegrees());

      // Apply update
      m_wheelOnlyPoseEstimator.updateWithTime(sampleTimestamps[i], m_trackedRotation, positions);
    }
  }

  private ChassisSpeeds getVelocitySpeeds() {
    return DriveConstants.kDriveKinematics.toChassisSpeeds(getModuleStates());
  }

  public void zeroHeading() {
    gyroIO.zero();
    // If no gyro is connected we have to manually reset our tracked rotation.
    if (!gyroInputs.connected) {
      m_trackedRotation = new Rotation2d();
    }
  }

  private Twist2d getVelocityTwist() {
    return GeomUtils.toTwist2d(getVelocitySpeeds());
  }

  public Pose2d getPredictedPose(double lookaheadTimeSeconds) {
    Twist2d velocity = getVelocityTwist();
    return getPose()
        .exp(
            new Twist2d(
                velocity.dx * lookaheadTimeSeconds,
                velocity.dy * lookaheadTimeSeconds,
                velocity.dtheta * lookaheadTimeSeconds));
  }

  public Pose2d getPredictedPose() {
    return getPredictedPose(kLookaheadTimeSeconds);
  }

  /**
   * Runs the drive at the desired velocity.
   *
   * @param speeds Speeds in meters/sec
   */
  public void runVelocity(ChassisSpeeds speeds) {

    // // Calculate module setpoints
    ChassisSpeeds discreteSpeeds = ChassisSpeeds.discretize(speeds, 0.02);
    SwerveModuleState[] setpointStates = kinematics.toSwerveModuleStates(discreteSpeeds);
    SwerveDriveKinematics.desaturateWheelSpeeds(setpointStates, MAX_LINEAR_SPEED);

    // Send setpoints to modules
    SwerveModuleState[] optimizedSetpointStates = new SwerveModuleState[4];

    for (int i = 0; i < 4; i++) {
      // The module returns the optimized state, useful for logging

      optimizedSetpointStates[i] = modules[i].runSetpoint(setpointStates[i]);
    }

    // Log setpoint states
    Logger.recordOutput("SwerveStates/Setpoints", setpointStates);
    // Logger.recordOutput("SwerveStates/SetpointsOptimized", optimizedSetpointStates);
  }

  public void runVelocityField(ChassisSpeeds speeds) {
    runVelocity(ChassisSpeeds.fromFieldRelativeSpeeds(speeds, m_trackedRotation));
  }

  /** Stops the drive. */
  public void stop() {
    runVelocity(new ChassisSpeeds());
  }

  /**
   * Stops the drive and turns the modules to an X arrangement to resist movement. The modules will
   * return to their normal orientations the next time a nonzero velocity is requested.
   */
  public void stopWithX() {
    Rotation2d[] headings = new Rotation2d[4];
    for (int i = 0; i < 4; i++) {
      headings[i] = getModuleTranslations()[i].getAngle();
    }
    kinematics.resetHeadings(headings);
    stop();
  }

  /** Returns a command to run a quasistatic test in the specified direction. */
  public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
    return sysId.quasistatic(direction);
  }

  /** Returns a command to run a dynamic test in the specified direction. */
  public Command sysIdDynamic(SysIdRoutine.Direction direction) {
    return sysId.dynamic(direction);
  }

  /** Returns the module states (turn angles and drive velocities) for all of the modules. */
  // @AutoLogOutput(key = "SwerveStates/Measured")
  // private SwerveModuleState[] getModuleStates() {
  //   SwerveModuleState[] states = new SwerveModuleState[4];
  //   for (int i = 0; i < 4; i++) {
  //     states[i] = modules[i].getState();
  //   }
  //   return states;
  // }

  // /** Returns the module positions (turn angles and drive positions) for all of the modules. */
  // private SwerveModulePosition[] getModulePositions() {
  //   SwerveModulePosition[] positions = new SwerveModulePosition[modules.length];
  //   for (int i = 0; i < 4; i++) {
  //     positions[i] = m_moduleInputs[i].position;
  //   }
  //   return positions;
  // }

  /** Returns the current odometry pose. */
  @AutoLogOutput(key = "Odometry/Robot")
  public Pose2d getPose() {
    // return Module.getAngle();
    return m_combinedPoseEstimator.getEstimatedPosition();
  }

  /** Returns the current odometry rotation. */
  public Rotation2d getRotation() {
    return getPose().getRotation();
  }

  /** Resets the current odometry pose. */
  public void setPose(Pose2d pose) {
    m_poseEstimators.forEach(
        poseEstimator -> {
          poseEstimator.resetPosition(m_trackedRotation, getModulePositions(), pose);
        });
  }

  /**
   * Adds a vision measurement to the pose estimator.
   *
   * @param visionPose The pose of the robot as measured by the vision camera.
   * @param timestamp The timestamp of the vision measurement in seconds.
   */
  // public void addVisionMeasurement(Pose2d visionPose, double timestamp) {
  //   poseEstimator.addVisionMeasurement(visionPose, timestamp);
  // }

  /** Returns the maximum linear speed in meters per sec. */
  public double getMaxLinearSpeedMetersPerSec() {
    return MAX_LINEAR_SPEED;
  }

  /** Returns the maximum angular speed in radians per sec. */
  public double getMaxAngularSpeedRadPerSec() {
    return MAX_ANGULAR_SPEED;
  }

  /** Returns an array of module translations. */
  public static Translation2d[] getModuleTranslations() {
    return new Translation2d[] {
      new Translation2d(TRACK_WIDTH_X / 2.0, TRACK_WIDTH_Y / 2.0),
      new Translation2d(TRACK_WIDTH_X / 2.0, -TRACK_WIDTH_Y / 2.0),
      new Translation2d(-TRACK_WIDTH_X / 2.0, TRACK_WIDTH_Y / 2.0),
      new Translation2d(-TRACK_WIDTH_X / 2.0, -TRACK_WIDTH_Y / 2.0)
    };
  }
}
