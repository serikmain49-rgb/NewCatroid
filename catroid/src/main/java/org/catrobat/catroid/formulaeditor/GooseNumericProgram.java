package org.catrobat.catroid.formulaeditor;

/** Bounded, ephemeral lowering of side-effect-free numeric literal trees. */
final class GooseNumericProgram {
    private static final int LIMIT = 256;
    final int[] ops = new int[LIMIT];
    final double[] values = new double[LIMIT];
    final double[] result = new double[1];
    int size, nodes;

    void reset() { size = 0; nodes = 0; }

    boolean append(FormulaElement element) {
        if (element == null || ++nodes > LIMIT) return false;
        if (element.type == FormulaElement.ElementType.NUMBER) {
            Double number = element.gooseNumericLiteral();
            if (number == null) return false;
            ops[size] = 0;
            values[size++] = number == Double.POSITIVE_INFINITY ? Double.MAX_VALUE
                    : number == Double.NEGATIVE_INFINITY ? -Double.MAX_VALUE : number;
            return true;
        }
        if (element.type == FormulaElement.ElementType.BRACKET) {
            if (element.additionalChildren == null) return false;
            int count = element.additionalChildren.size();
            return append(count == 0 ? element.rightChild : element.additionalChildren.get(count - 1));
        }
        String value = element.gooseRawValue();
        if (element.type != FormulaElement.ElementType.OPERATOR || value == null || element.rightChild == null)
            return false;
        final int op;
        switch (value) {
            case "PLUS": op = 1; break;
            case "MINUS": op = element.leftChild == null ? 5 : 2; break;
            case "MULT": op = 3; break;
            case "DIVIDE": op = 4; break;
            case "GREATER_THAN": op = 6; break;
            case "GREATER_OR_EQUAL": op = 7; break;
            case "SMALLER_THAN": op = 8; break;
            case "SMALLER_OR_EQUAL": op = 9; break;
            case "LOGICAL_AND": op = 10; break;
            case "LOGICAL_OR": op = 11; break;
            case "LOGICAL_NOT":
                if (element.leftChild != null) return false;
                op = 12; break;
            case "EQUAL": op = 13; break;
            case "NOT_EQUAL": op = 14; break;
            default: return false;
        }
        if (op != 5 && op != 12 && !append(element.leftChild)) return false;
        if (!append(element.rightChild)) return false;
        if (size >= LIMIT) return false;
        ops[size++] = op;
        return true;
    }
}
