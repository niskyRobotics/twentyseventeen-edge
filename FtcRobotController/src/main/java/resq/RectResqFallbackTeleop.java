package resq;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.Range;
import ftc.team6460.javadeck.ftc.Utils;

/**
 * Created by akh06977 on 9/18/2015.
 */

public class RectResqFallbackTeleop extends RectResqCommon {


    double scaledPower;
    Servo btnPushSrvo; // Left servo, labeled 2
    boolean pushServoDeployed = false;
    double aimPos = 0.32;
    Servo aimServo; // Lift servo
    Servo lLvr, rLvr;
    @Override
    public void init() {
        super.init();
        try {
            btnPushSrvo = hardwareMap.servo.get("btnSrvo");
        } catch (Exception e) {
            telemetry.addData("INITFAULT", "BTNSERVO");
        }
        try {

            aimServo = hardwareMap.servo.get("aimServo");
        } catch (Exception e) {
            telemetry.addData("INITFAULT", "BTNSERVO");
        }
        try {

            lLvr = hardwareMap.servo.get("lLever");
            rLvr = hardwareMap.servo.get("rLever");
        } catch (Exception e) {
            telemetry.addData("INITFAULT", "LEVER");
        }

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this.hardwareMap.appContext);
        scaledPower = Utils.getSafeDoublePref("lowspeed_power_scale", sharedPref, 0.50);
        this.gamepad1.setJoystickDeadzone(0.1f);

    }
    boolean lLevOut = false;
    boolean rLevOut = false;
    @Override
    public void loop() {

        double scaleActual = (this.gamepad1.right_trigger > 0.2) ? scaledPower : 1.00;
        boolean fullOverrideNeg = (this.gamepad1.right_trigger > 0.2);
        boolean fullOverridePos = (this.gamepad1.left_trigger > 0.2);
        double tipPreventionPower = 0;
        double lCalculated = this.gamepad1.left_stick_y * scaleActual + tipPreventionPower;

        double rCalculated = this.gamepad1.right_stick_y * scaleActual + tipPreventionPower;
        if(this.gamepad2.dpad_left) {
            lLevOut = true;
            rLevOut = false;
        } else if(this.gamepad2.dpad_right) {
            lLevOut = false;
            rLevOut = true;
        } else if(this.gamepad2.dpad_up) {
            lLevOut = false;
            rLevOut = false;
        }


        if (fullOverrideNeg) {
            lCalculated = -1;
            rCalculated = -1;
        } else if (fullOverridePos) {
            lCalculated = 1;
            rCalculated = 1;
        }

        l0.setPower(lCalculated);
        r0.setPower(rCalculated);

        l1.setPower(lCalculated);
        r1.setPower(rCalculated);

        l2.setPower(lCalculated);
        r2.setPower(rCalculated);

        //self explanatory winch

        w.setPower(this.gamepad2.right_stick_y);
        telemetry.addData("w", "1");


        aimPos -= this.gamepad2.left_stick_y / 512;
        aimPos = Range.clip(aimPos, 0.32, 0.92);
        telemetry.addData("aimPos", aimPos);
        if (aimServo != null)
            aimServo.setPosition(aimPos);

        if (btnPushSrvo != null) btnPushSrvo.setPosition(pushServoDeployed ? 0.091 : 0.365);
        telemetry.addData("WARN", "LEVERS NOT CALIBRATED!");
        if (lLvr != null) lLvr.setPosition(lLevOut ? 0:0); // TODO calibrate
        if (rLvr != null) rLvr.setPosition(rLevOut ? 0:0); // TODO calibrate
    }


}
