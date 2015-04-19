package com.redhat.ceylon.compiler.typechecker.analyzer;

import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.ASTRING_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.AVERBATIM_STRING;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_END;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_MID;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_START;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.VERBATIM_STRING;
import static java.lang.Character.isWhitespace;
import static java.lang.Character.toChars;
import static java.lang.Integer.parseInt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CharLiteral;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Literal;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.QuotedLiteral;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.StringLiteral;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.StringTemplate;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class LiteralVisitor extends Visitor {

    private int indent;
    static final Pattern DOC_LINK_PATTERN = 
            Pattern.compile("\\[\\[(([^\"`|\\[\\]]*\\|)?((module )|(package )|(class )|(interface )|(function )|(value )|(alias ))?(((\\w|\\.)+)::)?(\\w*)(\\.(\\w*))*(\\(\\))?)\\]\\]");
    private static Pattern CHARACTER_ESCAPE_PATTERN = 
            Pattern.compile("\\\\(\\{#([^}]*)\\}|\\{([^#]([^}]*))\\}|(.))");
    
    
    @Override
    public void visit(CompilationUnit that) {
        if (!that.getLiteralsProcessed()) {
            super.visit(that);
            that.setLiteralsProcessed(true);
        }
    }
    
    @Override
    public void visit(StringLiteral that) {
        if (that.getToken()==null) return;
        int type = that.getToken().getType();
        String text = that.getText();
        
        if (type==AVERBATIM_STRING || type==ASTRING_LITERAL) {
            Matcher m = DOC_LINK_PATTERN.matcher(text);
            while (m.find()) {
                String group = m.group(1);
                int start = that.getStartIndex()+m.start(1);
                int end = that.getStartIndex()+m.end(1);
                String[] linesUpTo = 
                        text.substring(0, m.start(1)).split("\n");
                CommonToken token = 
                        new CommonToken(ASTRING_LITERAL, group);
                token.setStartIndex(start);
                token.setStopIndex(end-1);
                token.setTokenIndex(that.getToken().getTokenIndex());
                int line = 
                        that.getToken().getLine() +
                        linesUpTo.length-1;
                int charInLine = 
                        linesUpTo.length==0 ? 0 : 
                            linesUpTo[linesUpTo.length-1].length();
                token.setLine(line);
                token.setCharPositionInLine(charInLine);
                that.addDocLink(new Tree.DocLink(token));
            }
        }
        
        if (type!=STRING_MID && 
            type!=STRING_END) {
            indent = getIndentPosition(that);
        }
        if (type==VERBATIM_STRING || 
            type==AVERBATIM_STRING) {
            text = text.substring(3,
                    text.length()-(text.endsWith("\"\"\"")?3:0));
        }
        else if (type==STRING_MID) {
            text = text.substring(2, 
                    text.length()-2);
        }
        else if (type==STRING_END) {
            text = text.substring(2, 
                    text.length()-(text.endsWith("\"")?1:0));
        }
        else if (type==STRING_START) {
            text = text.substring(1, 
                    text.length()-2);
        }
        else {
            text = text.substring(1, 
                    text.length()-(text.endsWith("\"")?1:0));
        }
        StringBuilder result = new StringBuilder();
        boolean allTrimmed = 
                stripIndent(text, indent, result);
        if (!allTrimmed) {
            that.addError("multiline string content should align with start of string: string begins at character position " + indent, 6000);
        }
        if (type!=VERBATIM_STRING && 
            type!=AVERBATIM_STRING) {
            interpolateEscapes(result, that);
        }
        that.setText(result.toString());
        if (type!=STRING_MID && 
            type!=STRING_START) {
            indent = 0;
        }        
    }

    @Override
    public void visit(StringTemplate that) {
        int oi = indent;
        indent = 0;
        super.visit(that);
        indent = oi;
    }
    
    @Override
    public void visit(QuotedLiteral that) {
        StringBuilder result = new StringBuilder();
        stripIndent(that.getText(), 
                getIndentPosition(that), result);
        //interpolateEscapes(result, that);
        that.setText(result.toString());
    }
    
	private int getIndentPosition(Literal that) {
		Token token = that.getToken();
		return token==null ? 
		        0 : token.getCharPositionInLine() +
		                getQuoteLength(token);
	}

    private int getQuoteLength(Token token) {
        int type = token.getType();
        return type==VERBATIM_STRING || 
                type==AVERBATIM_STRING ? 
                        3 : 1;
    }
    
    @Override
    public void visit(CharLiteral that) {
        StringBuilder result = 
                new StringBuilder(that.getText());
        interpolateEscapes(result, that);
        that.setText(result.toString());
    }
    
    static final String digits = "\\d+";
    static final String groups = "\\d{1,3}(_\\d{3})+";
    static final String fractionalGroups = "(\\d{3}_)+\\d{1,3}";
    static final String magnitude = "k|M|G|T|P";
    static final String fractionalMagnitude = "m|u|n|p|f";
    static final String exponent = "(e|E)(\\+|-)?" + digits;

    static final String hexDigits = "(\\d|[a-f]|[A-F])+";
    static final String hexGroups = "(\\d|[a-f]|[A-F]){1,4}(_(\\d|[a-f]|[A-F]){4})+|(\\d|[a-f]|[A-F]){1,2}(_(\\d|[a-f]|[A-F]){2})+";
    
    static final String binDigits = "(0|1)+";
    static final String binGroups = "(0|1){1,4}(_(0|1){4})+";
    
    @Override
    public void visit(Tree.NaturalLiteral that) {
        super.visit(that);
        String text = that.getToken().getText();
        if (!text.matches("^(" + digits + "|" + groups + ")(" + magnitude + ")?$") &&
            !text.matches("#(" + hexDigits + "|" + hexGroups + ")") &&
            !text.matches("\\$(" + binDigits + "|" + binGroups + ")")) {
            that.addError("illegal integer literal format");
        }        
        that.setText(that.getText()
                .replace("_", "")
                .replace("k", "000")
                .replace("M", "000000")
                .replace("G", "000000000")
                .replace("T", "000000000000")
                .replace("P", "000000000000000"));
    }
    
    @Override
    public void visit(Tree.FloatLiteral that) {
        super.visit(that);
        String text = that.getToken().getText();
        if (!text.matches("^(" + digits + "|" + groups + ")(\\.(" + 
                digits + "|" + fractionalGroups  + ")(" + 
                magnitude + "|" + fractionalMagnitude + "|" + exponent + ")?|" +
                fractionalMagnitude + ")$")) {
            that.addError("illegal floating literal format");
        }
        that.setText(that.getText()
                .replace("_", "")
                .replace("k", "e+3")
                .replace("M", "e+6")
                .replace("G", "e+9")
                .replace("T", "e+12")
                .replace("P", "e+15")
                .replace("m", "e-3")
                .replace("u", "e-6")
                .replace("n", "e-9")
                .replace("p", "e-12")
                .replace("f", "e-15"));
    }
        
    private static boolean stripIndent(final String text, 
            final int indentation, final StringBuilder result) {
        boolean correctlyIndented = true;
        int num = 0;
        for (String line: text.split("\n|\r\n?")) {
            if (num++==0) {
                result.append(line);
            }
            else {
                for (int i = 0; i < line.length(); i++) {
                    if (i < indentation) {
                        if (!isWhitespace(line.charAt(i))) {
                            correctlyIndented = false;
                            result.append(line.substring(i));
                            break;
                        }
                    } else {
                        result.append(line.substring(indentation));
                        break;
                    }
                }
            }
            result.append("\n");
        }
        result.setLength(result.length()-1);
        return correctlyIndented;
    }
    
    private static void interpolateEscapes(StringBuilder result, 
            Node node) {
        Matcher matcher;
        int start=0;
        while ((matcher = 
                CHARACTER_ESCAPE_PATTERN.matcher(result))
                                        .find(start)) {
            String hex = matcher.group(2);
            String name = matcher.group(3);
            if (name!=null) {
                boolean found=false;
                for (int codePoint=0; 
                        codePoint<=0xE01EF; 
                        codePoint++) {
                    String cn = Character.getName(codePoint);
                    if (cn!=null && cn.equals(name)) {
                        char[] chars = toChars(codePoint);
                        result.replace(matcher.start(), 
                                matcher.end(), 
                                new String(chars));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    int emoji = -1; 
                    switch (name) {
                    case ":)":
                    case ":-)":
                    case "=)":
                        emoji = 0x1f603; break;
                    case "O:)":
                    case "O:-)":
                    case "O=)":
                        emoji = 0x1f607; break;
                    case "}:)":
                    case "}:-)":
                    case "}=)":
                        emoji = 0x1f608; break;
                    case ":-(":
                    case ":(":
                    case "=(":
                        emoji = 0x1f61e; break;
                    case ":-|":
                    case ":|":
                    case "=|":
                        emoji = 0x1f610; break;
                    case ";-)":
                    case ";)":
                        emoji = 0x1f609; break;
                    case "B-)":
                    case "B)":
                        emoji = 0x1f60e; break;
                    case ":-D":
                    case ":D":
                        emoji = 0x1f600; break;
                    case "=D":
                        emoji = 0x1f604; break;
                    case "-_-":
                        emoji = 0x1f611; break;
                    case "o_o":
                        emoji = 0x1f613; break;
                    case "u_u":
                        emoji = 0x1f614; break;
                    case ">_<":
                        emoji = 0x1f623; break;
                    case "^_^":
                        emoji = 0x1f601; break;
                    case "^_^;;":
                        emoji = 0x1f605; break;
                    case "<3":
                        emoji = 0x1f49c; break;
                    case "<\\3":
                    case "</3":
                        emoji = 0x1f494; break;
                    case "~@~":
                        emoji = 0x1f4a9; break;
                    case "(]:{":
                        emoji = 0x1f473; break;
                    case "-<@%":
                        emoji = 0x1f41d; break;
                    case ":(|)":
                        emoji = 0x1f435; break;
                    case ":(:)":
                        emoji = 0x1f437; break;
                    case ":*":
                    case ":-*":
                        emoji = 0x1f617; break;
                    case ";*":
                    case ";-*":
                        emoji = 0x1f618; break;
                    case ":\\":
                    case ":-\\":
                    case "=\\":
                    case ":/":
                    case ":-/":
                    case "=/":
                        emoji = 0x1f615; break;
                    case ":S":
                    case ":-S":
                    case ":s":
                    case ":-s":
                        emoji = 0x1f616; break;
                    case ":P":
                    case ":-P":
                    case "=P":
                    case ":p":
                    case ":-p":
                    case "=p":
                        emoji = 0x1f61b; break;
                    case ";P":
                    case ";-P":
                    case ";p":
                    case ";-p":
                        emoji = 0x1f61c; break;
                    case ">.<":
                    case ">:(":
                    case ">:-(":
                    case ">=(":
                        emoji = 0x1f621; break;
                    case "T_T":
                    case ":'(":
                    case ";_;":
                    case "='(":
                        emoji = 0x1f622; break;
                    case "D:":
                        emoji = 0x1f626; break;
                    case "o.o":
                    case ":o":
                    case ":-o":
                    case "=o":
                        emoji = 0x1f62e; break;
                    case "O.O":
                    case ":O":
                    case ":-O":
                    case "=O":
                        emoji = 0x1f632; break;
                    case "x_x":
                    case "X-O":
                    case "x-o":
                    case "X(":
                    case "X-(":
                        emoji = 0x1f635; break;
                    case ":X)":
                    case ":3":
                    case "(=^..^=)":
                    case "(=^.^=)":
                    case "=^_^=":
                        emoji = 0x1f638; break;
                    default:
                        node.addError("illegal unicode escape sequence: " + 
                                name + " is not a Unicode character");
                    }
                    if (emoji>0) {
                        result.replace(matcher.start(), matcher.end(), 
                                new String(Character.toChars(emoji)));
                    }
                }
            }
            else if (hex!=null) {
                if (hex.length()!=2 && 
                    hex.length()!=4 &&
                    hex.length()!=6 && 
                    hex.length()!=8) { //tolerate 8 digits for backward compatibility only!
                    node.addError("illegal unicode escape sequence: must consist of 2, 4, or 6 digits");
                }
                else {
                    int codePoint=0;
                    try {
                        codePoint = parseInt(hex, 16);
                    }
                    catch (NumberFormatException nfe) {
                        node.addError("illegal unicode escape sequence: '" + 
                                hex + "' is not a hexadecimal number");
                    }
                    char[] chars;
                    try {
                        chars = toChars(codePoint);
                    }
                    catch (IllegalArgumentException iae) {
                        node.addError("illegal unicode escape sequence: '" + 
                                hex + "' is not a valid Unicode code point");
                        chars = toChars(0);
                    }
                    result.replace(matcher.start(), matcher.end(), 
                            new String(chars));
                }
            }
            else {
                char escape = matcher.group(5).charAt(0);
                char ch;
                switch (escape) {
                    case 'b': ch = '\b'; break;
                    case 't': ch = '\t'; break;
                    case 'n': ch = '\n'; break;
                    case 'f': ch = '\f'; break;
                    case 'r': ch = '\r'; break;
                    case '"':
                    case '\'':
                    case '`':
                    case '\\':
                    	ch = escape; break;
                    default:
                    	node.addError("illegal escape sequence: \\" + escape);
                    	ch='?';
                }
                result.replace(matcher.start(), matcher.end(), 
                        Character.toString(ch));
            }
            start = matcher.start()+1;
        }
    }
    
    @Override
    public void visit(Tree.Identifier that) {
        super.visit(that);
        String text = that.getText();
        int index = 0;
        while (index<text.length()) {
            int cp = text.codePointAt(index);
            index += Character.charCount(cp);
            int type = Character.getType(cp);
            boolean num = 
                    type==Character.LETTER_NUMBER ||
                    type==Character.DECIMAL_DIGIT_NUMBER ||
                    type==Character.OTHER_NUMBER;
            boolean letter = 
                    type==Character.LOWERCASE_LETTER ||
                    type==Character.UPPERCASE_LETTER ||
                    type==Character.TITLECASE_LETTER ||
                    type==Character.OTHER_LETTER||
                    type==Character.MODIFIER_LETTER;
            boolean us = cp=='_';
            if (index==0 && num) {
                that.addError("identifier may not begin with a digit");
                break;
            }
            else if (!num && !letter && !us) {
                that.addError("identifier must be composed of letters, digits, and underscores");
                break;
            }
        }
    }
    
}
