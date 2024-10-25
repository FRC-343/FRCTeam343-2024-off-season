package frc.robot;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import frc.robot.commands.PositionWithAmp;
import frc.robot.commands.StrafeAndAimToPose;
import frc.robot.subsystems.drive.Drive;
import frc.robot.subsystems.vision.apriltag.OffsetTags;
import frc.util.GarageUtils;
import java.util.Set;

public class DriverAutomationFactory {
  private final CommandXboxController driverController;
  // private final CommandCustomController operatorController;

  private final Drive drive;

  public DriverAutomationFactory(CommandXboxController controller, Drive drive) {
    this.driverController = controller;
    // this.operatorController = operatorController;
    this.drive = drive;
  }

  public Command ampPath() {
    return OffsetTags.AMP.getDeferredCommand();
  }

  public Command ampAssist() {
    return Commands.defer(
        () -> new PositionWithAmp(() -> -driverController.getLeftX(), drive, OffsetTags.AMP),
        Set.of(drive));
  }

  public Command stageHumanPath() {
    return OffsetTags.STAGE_HUMAN.getDeferredCommand();
  }

  public Command stageAmpPath() {
    return OffsetTags.STAGE_AMP.getDeferredCommand();
  }

  public Command stageCenterPath() {
    return OffsetTags.STAGE_CENTER.getDeferredCommand();
  }

  public Command stageLeftPath() {
    return Commands.deferredProxy(
        () ->
            GarageUtils.isBlueAlliance()
                ? OffsetTags.STAGE_AMP.getDeferredCommand()
                : OffsetTags.STAGE_HUMAN.getDeferredCommand());
  }

  public Command stageRightPath() {
    return Commands.deferredProxy(
        () ->
            GarageUtils.isBlueAlliance()
                ? OffsetTags.STAGE_HUMAN.getDeferredCommand()
                : OffsetTags.STAGE_AMP.getDeferredCommand());
  }

  public Command humanPlayerStationPath() {
    return Commands.deferredProxy(() -> OffsetTags.HUMAN_PLAYER.getDeferredCommand());
  }

  public Command strafeAndAimToAmpFeed() {
    return new StrafeAndAimToPose(
        () -> -driverController.getLeftY(),
        () -> -driverController.getLeftX(),
        OffsetTags.FLOOR_SHOT::getOffsetPose,
        drive,
        true);
  }
}
