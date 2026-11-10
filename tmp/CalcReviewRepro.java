import com.ahmedismail.flowtrack.util.*;
import com.ahmedismail.flowtrack.data.reference.*;

// Run with JDK 21 and the app's compiled Kotlin classes plus kotlin-stdlib.
class CalcReviewRepro {
    public static void main(String[] args) {
        var steep = new B313MiterInput(20, 138, 1, 1, 1.5,
            new MiterGeometry(120, 2, 114.3, 6.02, 150));
        expectRejected("Unsupported steep close miter", () -> B313MiterCalculator.INSTANCE.calculate(steep));
        var tight = new B313MiterInput(20, 138, 1, 1, 1.5,
            new MiterGeometry(45, 2, 114.3, 6.02, 10));
        expectRejected("Close miter R1 < r2/2", () -> B313MiterCalculator.INSTANCE.calculate(tight));
        var conditions = new B311MiterConditions(60, 6.02, true, true, true, true);
        var qualified = B311MiterCalculator.INSTANCE.calculate(8, 22.5, conditions);
        if (!qualified.getQualificationRequired() || qualified.getPasses() != null) {
            throw new AssertionError("Above-limit pressure must require qualification, without a verdict.");
        }
        System.out.println("PASS: B31.1 at 8 bar requires qualification.");
        var vessel = new VesselInput(10, 500, 138, 1, 0, null, null, 30, 2);
        expectRejected("Hemispherical MAWP t/R=0.4", () -> VesselThicknessCalculator.INSTANCE.maximumAllowablePressureBar(VesselPart.HEMISPHERICAL_HEAD, vessel, 200));
        var invalidVessel = new VesselInput(10, 500, 138, 2, 0, null, null, 30, 2);
        expectRejected("Shell MAWP invalid E=2", () -> VesselThicknessCalculator.INSTANCE.maximumAllowablePressureBar(VesselPart.CYLINDRICAL_SHELL, invalidVessel, 5));
        var pipe = new B311PipeInput(20, 100, 100, 1, 1, 60, B311MaterialGroup.FERRITIC, 20, null);
        expectRejected("B31.1 required wall > D/2", () -> B311PipeThicknessCalculator.INSTANCE.calculate(pipe));
    }

    private static void expectRejected(String label, Runnable calculation) {
        try {
            calculation.run();
        } catch (IllegalArgumentException expected) {
            System.out.println("PASS: " + label + " rejected.");
            return;
        }
        throw new AssertionError(label + " must be rejected.");
    }
}
