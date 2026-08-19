package com.study.designpattern.behavioral;

/**
 * 解释器模式（Interpreter）用例（常用 + 不常用）
 *
 * 定义一个语言的文法，并建立一个解释器来解释该语言中的句子。
 * 适用：表达式求值、SQL/正则解析、规则引擎、配置文件 DSL。
 *
 * 本质是组合模式的特化：终结符表达式（数字）是叶子，非终结符表达式（运算符）是容器。
 * JDK 例子：java.util.regex.Pattern 就是正则语言解释器。
 */
public class InterpreterDemo {

    // ---------- 抽象表达式 ----------
    public interface Expression {
        int interpret();
    }

    /** 终结符：数字 */
    public static final class NumberExpr implements Expression {
        private final int value;

        public NumberExpr(int value) {
            this.value = value;
        }

        public int interpret() {
            return value;
        }
    }

    /** 非终结符：加法 */
    public static final class AddExpr implements Expression {
        private final Expression left;
        private final Expression right;

        public AddExpr(Expression left, Expression right) {
            this.left = left;
            this.right = right;
        }

        public int interpret() {
            return left.interpret() + right.interpret();
        }
    }

    /** 非终结符：减法 */
    public static final class SubtractExpr implements Expression {
        private final Expression left;
        private final Expression right;

        public SubtractExpr(Expression left, Expression right) {
            this.left = left;
            this.right = right;
        }

        public int interpret() {
            return left.interpret() - right.interpret();
        }
    }

    /** 非终结符：乘法 */
    public static final class MultiplyExpr implements Expression {
        private final Expression left;
        private final Expression right;

        public MultiplyExpr(Expression left, Expression right) {
            this.left = left;
            this.right = right;
        }

        public int interpret() {
            return left.interpret() * right.interpret();
        }
    }

    /** 不常用：解析器（递归下降，把字符串 "1+2*3" 解析成表达式树，遵循乘除优先、从左到右） */
    public static final class ExpressionParser {
        private final String input;
        private int pos;

        public ExpressionParser(String input) {
            this.input = input.replaceAll("\\s+", "");
            this.pos = 0;
        }

        /** 文法：expr = term (('+'|'-') term)* ; term = number (('*') number)* */
        public Expression parse() {
            Expression expr = parseTerm();
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == '+') {
                    pos++;
                    expr = new AddExpr(expr, parseTerm());
                } else if (c == '-') {
                    pos++;
                    expr = new SubtractExpr(expr, parseTerm());
                } else {
                    throw new IllegalArgumentException("意外的字符: " + c);
                }
            }
            return expr;
        }

        private Expression parseTerm() {
            Expression term = parseNumber();
            while (pos < input.length() && input.charAt(pos) == '*') {
                pos++;
                term = new MultiplyExpr(term, parseNumber());
            }
            return term;
        }

        private Expression parseNumber() {
            int start = pos;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("第 " + pos + " 位不是数字");
            }
            return new NumberExpr(Integer.parseInt(input.substring(start, pos)));
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 解释器：常用写法（手工组装表达式树） ==========");
        // 1 + 2 * 3
        Expression expr = new AddExpr(
                new NumberExpr(1),
                new MultiplyExpr(new NumberExpr(2), new NumberExpr(3)));
        System.out.println("  1 + 2 * 3 = " + expr.interpret());

        Expression expr2 = new SubtractExpr(
                new MultiplyExpr(new NumberExpr(10), new NumberExpr(2)),
                new NumberExpr(4));
        System.out.println("  (10*2) - 4 = " + expr2.interpret());

        System.out.println();
        System.out.println("========== 解释器：不常用写法（递归下降解析器） ==========");
        for (String s : new String[]{"1+2*3", "10-2-3", "2*3*4+1"}) {
            System.out.println("  parse(\"" + s + "\") = " + new ExpressionParser(s).parse().interpret());
        }
        System.out.println("  说明: java.util.regex.Pattern、Spring SpEL 都是解释器思想的工业实现");
    }
}
