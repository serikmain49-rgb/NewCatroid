package org.catrobat.catroid.formulaeditor;
import java.util.Random;
import org.catrobat.catroid.fast2d.GooseMovement;
import static org.catrobat.catroid.formulaeditor.FormulaElement.ElementType.*;

public final class GooseNumericProgramTest {
    private static final Random random = new Random(151492);
    private static final String[] OPS = {"PLUS", "MINUS", "MULT", "DIVIDE"};
    private static final String[] EDGES = {"0.0", "-0.0", "NaN", "Infinity", "-Infinity", "1e-300", "1e300"};
    private static FormulaElement literal() {
        String value = random.nextInt(4) == 0 ? EDGES[random.nextInt(EDGES.length)]
                : Double.toString(random.nextGaussian() * 1e100);
        return new FormulaElement(NUMBER, value, null, null);
    }
    private static FormulaElement tree(int depth) {
        if (depth == 0 || random.nextInt(6) == 0) return literal();
        if (random.nextInt(8) == 0) return new FormulaElement(BRACKET, null, null, tree(depth - 1));
        String op = OPS[random.nextInt(OPS.length)];
        FormulaElement left = op.equals("MINUS") && random.nextInt(5) == 0 ? null : tree(depth - 1);
        return new FormulaElement(OPERATOR, op, left, tree(depth - 1));
    }
    private static double normal(double d) {
        if (d == Double.POSITIVE_INFINITY) return Double.MAX_VALUE;
        if (d == Double.NEGATIVE_INFINITY) return -Double.MAX_VALUE;
        return d;
    }
    private static double javaValue(FormulaElement e) {
        if (e.type == NUMBER) return normal(e.gooseNumericLiteral());
        if (e.type == BRACKET) return javaValue(e.rightChild);
        double right;
        if (e.leftChild == null) return normal(-javaValue(e.rightChild));
        double left = javaValue(e.leftChild);
        right = javaValue(e.rightChild);
        switch (e.value) {
            case "PLUS": return normal(left + right);
            case "MINUS": return normal(left - right);
            case "MULT": return normal(left * right);
            case "DIVIDE": return normal(right == 0 ? Double.NaN : left / right);
            default: throw new AssertionError(e.value);
        }
    }
    public static void main(String[] args) {
        if (!GooseMovement.prepareGameThread())
            throw new AssertionError("could not initialize Goose on test thread");
        GooseNumericProgram program = new GooseNumericProgram();
        for (int i = 0; i < 20000; i++) {
            FormulaElement expression = tree(5);
            program.reset();
            if (!program.append(expression)) throw new AssertionError("lowering failed: " + i);
            if (!GooseMovement.formulaNumeric(program.ops, program.values, program.size, program.result))
                throw new AssertionError("native evaluation failed");
            double expected = javaValue(expression);
            if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(program.result[0]))
                throw new AssertionError("result differs: " + i + ": " + expected + " vs " + program.result[0]);
        }
        program.reset();
        if (program.append(new FormulaElement(USER_VARIABLE, "x", null, null)))
            throw new AssertionError("user variable must use original interpreter");
        program.reset();
        if (program.append(new FormulaElement(NUMBER, "not-a-number", null, null)))
            throw new AssertionError("non-numeric literal must use original interpreter");
        FormulaElement deep = literal();
        for (int i = 0; i < 257; i++) deep = new FormulaElement(BRACKET, null, null, deep);
        program.reset();
        if (program.append(deep)) throw new AssertionError("oversized tree accepted");
        System.out.println("PASS: 20,000 formula trees, edge values and unsupported cases");
    }
}
