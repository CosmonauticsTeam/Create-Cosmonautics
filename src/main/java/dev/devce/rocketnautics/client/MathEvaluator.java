package dev.devce.rocketnautics.client;

import java.util.Locale;

public class MathEvaluator {
    private final String expression;
    private int pos = -1, ch;

    public MathEvaluator(String expression) {
        this.expression = expression.toLowerCase(Locale.ROOT);
    }

    private void nextChar() {
        ch = (++pos < expression.length()) ? expression.charAt(pos) : -1;
    }

    private boolean eat(int charToEat) {
        while (ch == ' ') nextChar();
        if (ch == charToEat) {
            nextChar();
            return true;
        }
        return false;
    }

    public double evaluate(double t, double f, double p) {
        pos = -1;
        nextChar();
        try {
            return parseExpression(t, f, p);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double parseExpression(double t, double f, double p) {
        double x = parseTerm(t, f, p);
        for (;;) {
            if      (eat('+')) x += parseTerm(t, f, p); // addition
            else if (eat('-')) x -= parseTerm(t, f, p); // subtraction
            else return x;
        }
    }

    private double parseTerm(double t, double f, double p) {
        double x = parseFactor(t, f, p);
        for (;;) {
            if      (eat('*')) x *= parseFactor(t, f, p); // multiplication
            else if (eat('/')) {
                double val = parseFactor(t, f, p);
                x = (val == 0) ? 0 : x / val; // division
            }
            else if (eat('%')) {
                double val = parseFactor(t, f, p);
                x = (val == 0) ? 0 : x % val; // modulo
            }
            else return x;
        }
    }

    private double parseFactor(double t, double f, double p) {
        if (eat('+')) return parseFactor(t, f, p); // unary plus
        if (eat('-')) return -parseFactor(t, f, p); // unary minus

        double x;
        int startPos = this.pos;
        if (eat('(')) { // parentheses
            x = parseExpression(t, f, p);
            eat(')');
        } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
            while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
            x = Double.parseDouble(expression.substring(startPos, this.pos));
        } else if (ch >= 'a' && ch <= 'z') { // functions or variables
            while (ch >= 'a' && ch <= 'z') nextChar();
            String name = expression.substring(startPos, this.pos);
            if (eat('(')) {
                x = parseExpression(t, f, p);
                eat(')');
                x = switch (name) {
                    case "sin" -> Math.sin(x);
                    case "cos" -> Math.cos(x);
                    case "tan" -> Math.tan(x);
                    case "abs" -> Math.abs(x);
                    case "sqrt" -> Math.sqrt(x);
                    case "floor" -> Math.floor(x);
                    case "ceil" -> Math.ceil(x);
                    default -> 0.0;
                };
            } else {
                x = switch (name) {
                    case "t" -> t;
                    case "f" -> f;
                    case "p" -> p;
                    case "pi" -> Math.PI;
                    case "noise" -> Math.random() * 2.0 - 1.0;
                    default -> 0.0;
                };
            }
        } else {
            x = 0.0;
        }

        if (eat('^')) x = Math.pow(x, parseFactor(t, f, p)); // exponentiation

        return x;
    }
}
