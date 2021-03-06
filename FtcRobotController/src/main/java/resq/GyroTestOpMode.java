package resq;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.qualcomm.ftcrobotcontroller.opmodes.FtcOpModeRegister;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.apache.commons.codec.binary.Hex;
import org.swerverobotics.library.*;
import org.swerverobotics.library.interfaces.*;

/**
 * SynchIMUDemo gives a short demo on how to use the BNO055 Inertial Motion Unit (IMU) from AdaFruit.
 * http://www.adafruit.com/products/2472
 */
@TeleOp(name = "IMU Demo", group = "Swerve Examples")
public class GyroTestOpMode extends SynchronousOpMode {


    //----------------------------------------------------------------------------------------------
    // State
    //----------------------------------------------------------------------------------------------

    // Our sensors, motors, and other devices go here, along with other long term state
    IBNO055IMU imu;
    ElapsedTime elapsed = new ElapsedTime();
    IBNO055IMU.Parameters parameters = new IBNO055IMU.Parameters();

    // Here we have state we use for updating the dashboard. The first of these is important
    // to read only once per update, as its acquisition is expensive. The remainder, though,
    // could probably be read once per item, at only a small loss in display accuracy.
    EulerAngles angles;
    Position position;
    Acceleration accel;
    int loopCycles;
    int i2cCycles;
    double ms;
    private double xT;
    private double yT;
    private double zT;
    private int counts;
    //----------------------------------------------------------------------------------------------
    // main() loop


    //----------------------------------------------------------------------------------------------

    public void upd() {
        angles = imu.getAngularOrientation();
        position = imu.getPosition();
        accel = imu.getLinearAcceleration();
        // The rest of this is pretty cheap to acquire, but we may as well do it
        // all while we're gathering the above.
        loopCycles = getLoopCount();
        i2cCycles = 0;
        ms = elapsed.time() * 1000.0;
    }

    @Override
    public void main() throws InterruptedException {
        Log.e("TRACE", "In main");
        // We are expecting the IMU to be attached to an I2C port on  a core device interface
        // module and named "imu". Retrieve that raw I2cDevice and then wrap it in an object that
        // semantically understands this particular kind of sensor.
        parameters.angleUnit = IBNO055IMU.ANGLEUNIT.DEGREES;
        parameters.accelUnit = IBNO055IMU.ACCELUNIT.METERS_PERSEC_PERSEC;
        parameters.loggingEnabled = true;
        parameters.mode = IBNO055IMU.SENSOR_MODE.NDOF;
        parameters.loggingTag = "BNO055";
        imu = ClassFactory.createAdaFruitBNO055IMU(hardwareMap.i2cDevice.get("bno055"), parameters);
        // Enable reporting of position using the naive integrator

        // Set up our dashboard computations
        composeDashboard();

        // Wait until we're told to go
        while (!this.isStarted()) {
            upd();
            telemetry.update();
        }

        upd();
        long t = System.currentTimeMillis();
        xT = 0;
        yT = 0;
        zT = 0;
        counts = 0;
        while (System.currentTimeMillis() < t + 10000) {
            upd();

            telemetry.update();
            xT += accel.accelX;
            yT += accel.accelY;
            zT += accel.accelZ;
            counts++;
            idle();
        }
        running = true;
        imu.startAccelerationIntegration(new Position(), new Velocity());

        // Loop and update the dashboard
        while (opModeIsActive()) {
            upd();
            telemetry.update();
            if (!calSaved && isCalibrated(imu.read8(IBNO055IMU.REGISTER.CALIB_STAT))) {
                if(calCounts > 20) {
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this.hardwareMap.appContext);
                    SharedPreferences.Editor e = sharedPref.edit();
                    e.putString("gyrocalib", Hex.encodeHexString(imu.readCalibrationData()));
                    calSaved = e.commit();
                }
                calCounts++;
            }
            idle();
        }
    }
boolean calSaved = false;
    int calCounts = 0;
    boolean running = false;
    //----------------------------------------------------------------------------------------------
    // dashboard configuration
    //----------------------------------------------------------------------------------------------

    void composeDashboard() {
        // The default dashboard update rate is a little to slow for us, so we update faster
        telemetry.setUpdateIntervalMs(200);

        // At the beginning of each telemetry update, grab a bunch of data
        // from the IMU that we will then display in separate lines.
        telemetry.addAction(new Runnable() {
            @Override
            public void run() {
                // Acquiring the angles is relatively expensive; we don't want
                // to do that in each of the three items that need that info, as that's
                // three times the necessary expense.
                angles = imu.getAngularOrientation();
                position = imu.getPosition();
                accel = imu.getLinearAcceleration();
                // The rest of this is pretty cheap to acquire, but we may as well do it
                // all while we're gathering the above.
                loopCycles = getLoopCount();
                i2cCycles = 0;
                ms = elapsed.time() * 1000.0;
            }
        });
        telemetry.addLine(
                telemetry.item("loop count: ", new IFunc<Object>() {
                    public Object value() {
                        return loopCycles;
                    }
                }),
                telemetry.item("loop rate: ", new IFunc<Object>() {
                    public Object value() {
                        return formatRate(ms / loopCycles);
                    }
                })
                );

        telemetry.addLine(
                telemetry.item("status: ", new IFunc<Object>() {
                    public Object value() {
                        return decodeStatus(imu.getSystemStatus());
                    }
                }),
                telemetry.item("calib: ", new IFunc<Object>() {
                    public Object value() {
                        return decodeCalibration(imu.read8(IBNO055IMU.REGISTER.CALIB_STAT));
                    }
                }));

        telemetry.addLine(
                telemetry.item("heading: ", new IFunc<Object>() {
                    public Object value() {
                        return formatAngle(angles.heading);
                    }
                }),
                telemetry.item("roll: ", new IFunc<Object>() {
                    public Object value() {
                        return formatAngle(angles.roll);
                    }
                }),
                telemetry.item("pitch: ", new IFunc<Object>() {
                    public Object value() {
                        return formatAngle(angles.pitch);
                    }
                }));

        telemetry.addLine(
                telemetry.item("x: ", new IFunc<Object>() {
                    public Object value() {
                        return formatPosition(position.x);
                    }
                }),
                telemetry.item("y: ", new IFunc<Object>() {
                    public Object value() {
                        return formatPosition(position.y);
                    }
                }),
                telemetry.item("z: ", new IFunc<Object>() {
                    public Object value() {
                        return formatPosition(position.z);
                    }
                }));

        telemetry.addLine(
                telemetry.item("cal: ", new IFunc<Object>() {
                    public Object value() {
                        return imu.isSystemCalibrated();
                    }
                })
        );
        telemetry.addLine(
                telemetry.item("xa: ", new IFunc<Object>() {
                    public Object value() {
                        return formatPosition(accel.accelX);
                    }
                }),
                telemetry.item("ya: ", new IFunc<Object>() {
                    public Object value() {
                        return formatPosition(accel.accelY);
                    }
                }),
                telemetry.item("za: ", new IFunc<Object>() {
                    public Object value() {
                        return formatPosition(accel.accelZ);
                    }
                }));
        telemetry.addLine(
                telemetry.item("xav: ", new IFunc<Object>() {
                    public Object value() {
                        return formatPosition(xT / counts);
                    }
                }),
                telemetry.item("yav: ", new IFunc<Object>() {
                    public Object value() {
                        return formatPosition(yT / counts);
                    }
                }),
                telemetry.item("zav: ", new IFunc<Object>() {
                    public Object value() {
                        return formatPosition(yT / counts);
                    }
                }));
        telemetry.addLine(telemetry.item("calibSaved: ", new IFunc<Object>() {
            @Override
            public Object value() {
                return calSaved;
            }
        }));
    }

    String formatAngle(double angle) {
        return parameters.angleUnit == IBNO055IMU.ANGLEUNIT.DEGREES ? formatDegrees(angle) : formatRadians(angle);
    }

    String formatRadians(double radians) {
        return formatDegrees(degreesFromRadians(radians));
    }

    String formatDegrees(double degrees) {
        return String.format("%.1f", normalizeDegrees(degrees));
    }

    String formatRate(double cyclesPerSecond) {
        return String.format("%.2f", cyclesPerSecond);
    }

    String formatPosition(double coordinate) {
        String unit = parameters.accelUnit == IBNO055IMU.ACCELUNIT.METERS_PERSEC_PERSEC
                ? "m" : "??";
        return String.format("%.2f%s", coordinate, unit);
    }

    //----------------------------------------------------------------------------------------------
    // Utility
    //----------------------------------------------------------------------------------------------

    /**
     * Normalize the angle into the range [-180,180)
     */
    double normalizeDegrees(double degrees) {
        while (degrees >= 180.0) degrees -= 360.0;
        while (degrees < -180.0) degrees += 360.0;
        return degrees;
    }

    double degreesFromRadians(double radians) {
        return radians * 180.0 / Math.PI;
    }

    /**
     * Turn a system status into something that's reasonable to show in telemetry
     */
    String decodeStatus(int status) {
        switch (status) {
            case 0:
                return "idle";
            case 1:
                return "syserr";
            case 2:
                return "periph";
            case 3:
                return "sysinit";
            case 4:
                return "selftest";
            case 5:
                return "fusion";
            case 6:
                return "running";
        }
        return "unk";
    }

    /**
     * Turn a calibration code into something that is reasonable to show in telemetry
     */
    String decodeCalibration(int status) {
        StringBuilder result = new StringBuilder();

        result.append(String.format("s%d", (status >> 6) & 0x03));  // SYS calibration status
        result.append(" ");
        result.append(String.format("g%d", (status >> 4) & 0x03));  // GYR calibration status
        result.append(" ");
        result.append(String.format("a%d", (status >> 2) & 0x03));  // ACC calibration status
        result.append(" ");
        result.append(String.format("m%d", (status >> 0) & 0x03));  // MAG calibration status

        return result.toString();
    }

    public boolean isCalibrated(int status) {
        return (((status >> 6) & 0x03) == 0x03) && (((status >> 4) & 0x03) == 0x03) && (((status >> 2) & 0x03) == 0x03) && (((status >> 0) & 0x03) == 0x03);
    }
}
