package frc.robot.commands;

import edu.wpi.first.math.controller.HolonomicDriveController;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.math.trajectory.TrajectoryConfig;
import edu.wpi.first.math.trajectory.TrajectoryGenerator;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.Constants;
import frc.robot.subsystems.HolonomicChassisSim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class WaypointFollower extends CommandBase {

    private final HolonomicChassisSim chassisSim;
    private final Trajectory trajectory;
    private final HolonomicDriveController controller;
    private final Timer timer;
    private final Pose2d startingPose, endingPose, tolerance;

    public WaypointFollower(HolonomicChassisSim chassisSim, Translation2d ...waypoints){
        this(chassisSim, new Rotation2d(), new Rotation2d(), waypoints);
    }

    public WaypointFollower(HolonomicChassisSim chassisSim, Rotation2d startingRot, Rotation2d endingRot, Translation2d ...waypoints){
        if(waypoints.length < 2){
            System.out.println("bruhhhh wha da heeeeeelllllll aint no way blud tryna use 1 waypoint to generate a trajectory you goofy ahh go back to cs 1");
            this.cancel();
        }
        this.chassisSim = chassisSim;

        // Generate Trajectory
        this.startingPose = new Pose2d(waypoints[0], startingRot);
        this.endingPose = new Pose2d(waypoints[waypoints.length - 1], endingRot);

        List<Translation2d> midpoints = Arrays.asList(Arrays.copyOfRange(waypoints, 1, waypoints.length - 1));
        trajectory = TrajectoryGenerator.generateTrajectory(
                startingPose,
                midpoints,
                endingPose,
                new TrajectoryConfig(Constants.Simulation.MAX_AXIS_SPEED, Constants.Simulation.MAX_ACCELERATION));

        // Display Trajectory

        chassisSim.displayTrajectory(trajectory);
        addRequirements(chassisSim);

        // Create PID Controller
        controller = new HolonomicDriveController(
                new PIDController(0.1, 0, 0.1),
                new PIDController(0.1, 0, 0.1),
                new ProfiledPIDController(1, 0, 0,
                new TrapezoidProfile.Constraints(Constants.Simulation.MAX_ANGULAR_SPEED, Constants.Simulation.MAX_ANGULAR_ACCELERATION)));
        timer = new Timer();

        // Set Tolerance
        this.tolerance = new Pose2d(new Translation2d(0.03, 0.03), new Rotation2d(Units.degreesToRadians(2)));
        controller.setTolerance(tolerance);
    }

    @Override
    public void initialize() {
        timer.start();
        chassisSim.setRobotPose(trajectory.getInitialPose());
        SmartDashboard.putString("Status", "Running");
    }

    @Override
    public void execute() {
        ChassisSpeeds speeds = controller.calculate(chassisSim.getRobotPose(), trajectory.sample(timer.get()), chassisSim.getRobotPose().getRotation());
        SmartDashboard.putNumber("vx", speeds.vxMetersPerSecond);
        SmartDashboard.putNumber("vy", speeds.vyMetersPerSecond);
        SmartDashboard.putNumber("vomega", speeds.omegaRadiansPerSecond);
        SmartDashboard.putBoolean("On Target", controller.atReference());
        chassisSim.driveFromRobotOrientedChassisSpeeds(speeds, false);
    }

    @Override
    public boolean isFinished() {
        Transform2d diff = chassisSim.getRobotPose().minus(endingPose);
        return Math.abs(diff.getX()) <= tolerance.getX()
                && Math.abs(diff.getY()) <= tolerance.getY()
                && Math.abs(diff.getRotation().getDegrees()) <= tolerance.getRotation().getDegrees();
    }

    @Override
    public void end(boolean interrupted) {
        controller.setEnabled(false);
        chassisSim.driveFromFieldOrientedChassisSpeeds(new ChassisSpeeds(0, 0, 0));
        SmartDashboard.putString("Status", "Finished");
    }
}
