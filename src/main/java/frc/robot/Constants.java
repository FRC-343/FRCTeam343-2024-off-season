// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.pathplanner.lib.path.PathConstraints;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.RobotBase;

/**
 * The Constants class provides a convenient place for teams to hold robot-wide numerical or boolean
 * constants. This class should not be used for any other purpose. All constants should be declared
 * globally (i.e. public static). Do not put anything functional in this class.
 *
 * <p>It is advised to statically import this class (or one of its inner classes) wherever the
 * constants are needed, to reduce verbosity.
 */
public final class Constants {

  public static int pdp = 0;

  /** Command Scheduler loopback */
  public static double loopback = 0.02;

  public static final class AdvantageKitConstants {
    public static enum Mode {
      REAL,
      REPLAY,
      SIM
    }

    private static Mode kfakeMode = Mode.SIM;

    public static Mode getMode() {
      return RobotBase.isReal() ? Mode.REAL : kfakeMode;
    }
  }

  public static final class PathPlannerConstants {
    public static final Alliance DEFAULT_ALLIANCE = Alliance.Blue;

    public static final double kMaxAngularAcceleration = 4 * Math.PI; // This was made up
    public static final double kMaxAccelerationMetersPerSecondSquared = 3.00; // This was made up

    public static final PathConstraints DEFAULT_PATH_CONSTRAINTS =
        new PathConstraints(
            DriveConstants.kMaxSpeedMetersPerSecond,
            PathPlannerConstants.kMaxAccelerationMetersPerSecondSquared,
            DriveConstants.kMaxAngularSpeed,
            5 * Math.PI);

    public static final PathConstraints TEST_PATH_CONSTRAINTS =
        new PathConstraints(
            1.0,
            PathPlannerConstants.kMaxAccelerationMetersPerSecondSquared,
            DriveConstants.kMaxAngularSpeed,
            5 * Math.PI);
  }

  public static final class DriveConstants {
    // Driving Parameters - Note that these are not the maximum capable speeds of
    // the robot, rather the allowed maximum speeds
    public static final double kMaxSpeedMetersPerSecond = 4.6;
    public static final double kMaxAngularSpeed = 2 * Math.PI; // radians per second

    public static final double kDirectionSlewRate = 1.2; // radians per second
    public static final double kMagnitudeSlewRate = 1.8; // percent per second (1 = 100%)
    public static final double kRotationalSlewRate = 2.0; // percent per second (1 = 100%)

    // Chassis configuration
    // Distance between centers of right and left wheels on robot
    public static final double kTrackWidth = Units.inchesToMeters(24.5);
    // Distance between front and back wheels on robot
    public static final double kWheelBase = Units.inchesToMeters(24.5);
    public static final SwerveDriveKinematics kDriveKinematics =
        new SwerveDriveKinematics(
            new Translation2d(kWheelBase / 2, kTrackWidth / 2),
            new Translation2d(kWheelBase / 2, -kTrackWidth / 2),
            new Translation2d(-kWheelBase / 2, kTrackWidth / 2),
            new Translation2d(-kWheelBase / 2, -kTrackWidth / 2));

    // The radius of the drivetrain (distance from center to each module) (meters)
    public static final double kRadius =
        Math.hypot(DriveConstants.kWheelBase / 2, DriveConstants.kTrackWidth / 2);

    // Angular offsets of the modules relative to the chassis in radians
    public static final double kFrontLeftChassisAngularOffset = -Math.PI / 2;
    public static final double kFrontRightChassisAngularOffset = 0;
    public static final double kBackLeftChassisAngularOffset = Math.PI;
    public static final double kBackRightChassisAngularOffset = Math.PI / 2;

    // SPARK MAX CAN IDs
    public static final int kFrontLeftDrivingCanId = 15;
    public static final int kRearLeftDrivingCanId = 17;
    public static final int kFrontRightDrivingCanId = 11;
    public static final int kRearRightDrivingCanId = 13;

    public static final int kFrontLeftTurningCanId = 14;
    public static final int kRearLeftTurningCanId = 16;
    public static final int kFrontRightTurningCanId = 10;
    public static final int kRearRightTurningCanId = 12;

    public static final int kGyroCanId = 1;
  }
}
