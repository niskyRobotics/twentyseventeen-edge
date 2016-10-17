package org.usfirst.ftc.exampleteam.yourcodehere;

import com.qualcomm.robotcore.hardware.*;
import org.swerverobotics.library.*;
import org.swerverobotics.library.interfaces.*;

/**
 * A skeletal example of a do-nothing first OpMode. Go ahead and change this code
 * to suit your needs, or create sibling OpModes adjacent to this one in the same
 * Java package.
 */
@TeleOp(name = "My Very First OpMode")
public class MyFirstOpMode extends SynchronousOpMode {
    /* Declare here any fields you might find useful. */
    DcMotor motorLeftFront = null;
    DcMotor motorLeftBack = null;
    DcMotor motorRightFront = null;
    DcMotor motorRightBack = null;
    /*
    DcMotor catapult = null;
        DcMotor linearSlideOne = null;
        DcMotor linearSlideTwo = null;
        Servo buttonPusher = null;
        Servo ballPicker = null;
        */

    @Override
    public void main() throws InterruptedException {
        /* Initialize our hardware variables. Note that the strings used here as parameters
         * to 'get' must correspond to the names you assigned during the robot configuration
         * step you did in the FTC Robot Controller app on the phone.
         */
        this.motorLeftBack = this.hardwareMap.dcMotor.get("motorLeftBack");
        this.motorLeftFront = this.hardwareMap.dcMotor.get("motorLeftFront");
        this.motorRightBack = this.hardwareMap.dcMotor.get("motorRightBack");
        this.motorRightFront = this.hardwareMap.dcMotor.get("motorRightFront");
            /*
            this.catapult = this.hardwareMap.dcMotor.get("catapult");
            this.linearSlideOne = this.hardwareMap.dcMotor.get("linearSlideOne");
            this.linearSlideTwo = this.hardwareMap.dcMotor.get("linearSlideTwo");
            this.buttonPusher = this.hardwareMap.servo.get("buttonPusher");
            this.ballPicker = this.hardwareMap.servo.get("ballPicker");
            */
        this.motorLeftBack.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
        this.motorRightBack.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
        this.motorLeftFront.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
        this.motorRightFront.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
        this.motorRightBack.setDirection(DcMotor.Direction.REVERSE);
        this.motorRightFront.setDirection(DcMotor.Direction.REVERSE);

        // Wait for the game to start
        waitForStart();
        float startTime = System.currentTimeMillis();
        // Go go gadget robot!
        while (opModeIsActive()) {
            this.updateGamepads();

            this.motorLeftBack.setPower(this.gamepad1.left_stick_y);
            this.motorLeftFront.setPower(this.gamepad1.left_stick_y);
            this.motorRightBack.setPower(this.gamepad1.right_stick_y);
            this.motorRightFront.setPower(this.gamepad1.right_stick_y);
            telemetry.addData("Bob: ", this.gamepad1.right_stick_y);
            telemetry.addData("Joe: ", this.gamepad1.left_stick_y);

            boolean update = telemetry.update();
            this.idle();
        }
    }
}

