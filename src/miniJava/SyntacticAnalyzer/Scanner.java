package miniJava.SyntacticAnalyzer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import miniJava.ErrorReporter;

public class Scanner {
    private InputStream inputStream;
    private ErrorReporter reporter;

    private char currChar;
    private StringBuffer currSpelling;
    private TokenKind currKind;
    private int currLine;

    private boolean eot;

    public Scanner(InputStream inputStream, ErrorReporter reporter) {
        this.inputStream = inputStream;
        this.reporter = reporter;
        this.currLine = 1;

        eot = false;
        readChar();
    }

    public Token scan() {
        SourcePosition pos = new SourcePosition();
        pos.start = currLine;
        pos.finish = currLine;
        currSpelling = new StringBuffer("");
        currKind = scanToken();
        while(currKind == TokenKind.CMT || currKind == TokenKind.SP) {
            return scan();
        }
        return new Token(currKind, currSpelling.toString(), pos);
    }

    private TokenKind scanToken() {
        if(eot) return TokenKind.EOT;

        switch(currChar) {
            case ' ': case '\n': case '\t': case '\r':
                takeIt();
                return TokenKind.SP;

            case 'a': case 'b': case 'c': case 'd': case 'e': case 'f': case 'g': case 'h': case 'i': case 'j': case 'k': case 'l':
            case 'm': case 'n': case 'o': case 'p': case 'q': case 'r': case 's': case 't': case 'u': case 'v': case 'w': case 'x':
            case 'y': case 'z': case 'A': case 'B': case 'C': case 'D': case 'E': case 'F': case 'G': case 'H': case 'I': case 'J':
            case 'K': case 'L': case 'M': case 'N': case 'O': case 'P': case 'Q': case 'R': case 'S': case 'T': case 'U': case 'V':
            case 'W': case 'X': case 'Y': case 'Z':
                takeIt();
                while(isLetter(currChar) || isDigit(currChar) || isUnderscore(currChar)) takeIt();
                return TokenKind.ID;

            case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9': case '0':
                takeIt();
                while(isDigit(currChar)) takeIt();
                return TokenKind.NUM;

            case '+':
                takeIt();
                return TokenKind.ADD;

            case '-':
                takeIt();
                return TokenKind.MI;

            case '*':
                takeIt();
                return TokenKind.MUL;

            case '/':
                takeIt();
                if(currChar == '*'){
                    takeIt();
                    while(!eot) {
                        if(currChar != '*') takeIt();
                        else {
                            takeIt();
                            if(currChar == '/') {
                                takeIt();
                                return TokenKind.CMT;
                            }
                        }
                    }
                    scanError("Unclosed statement");
                    return TokenKind.ERR;
                }
                else if(currChar == '/'){
                    takeIt();
                    while(!eot && currChar != '\n') {
                        takeIt();
                    }
                    if(eot) return TokenKind.CMT;
                    take('\n');
                    return TokenKind.CMT;
                }
                else return TokenKind.DIV;

            case '>':
                takeIt();
                if(currChar == '='){
                    takeIt();
                    return TokenKind.GE;
                }
                return TokenKind.GT;

            case '<':
                takeIt();
                if(currChar == '=') {
                    takeIt();
                    return TokenKind.LE;
                }
                return TokenKind.LT;

            case '=':
                takeIt();
                if(currChar == '=') {
                    takeIt();
                    return TokenKind.EQ;
                }
                return TokenKind.IS;

            case '!':
                takeIt();
                if(currChar == '=') {
                    takeIt();
                    return TokenKind.NE;
                }
                return TokenKind.NOT;

            case '&':
                takeIt();
                take('&');
                return TokenKind.AND;

            case '|':
                takeIt();
                take('|');
                return TokenKind.OR;

            case '(':
                takeIt();
                return TokenKind.LP;

            case ')':
                takeIt();
                return TokenKind.RP;

            case '[':
                takeIt();
                return TokenKind.LB;

            case ']':
                takeIt();
                return TokenKind.RB;

            case '{':
                takeIt();
                return TokenKind.LC;

            case '}':
                takeIt();
                return TokenKind.RC;

            case ';':
                takeIt();
                return TokenKind.SC;

            case '.':
                takeIt();
                return TokenKind.DOT;

            case ',':
                takeIt();
                return TokenKind.COM;

            default:
                scanError("Unrecognized character '" + currChar + "' in input");
                return TokenKind.ERR;
        }
    }

    private boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isUnderscore(char c) {
        return c == '_';
    }

    private boolean isASCii(char c) {
        return (int)c >= 0 && (int)c <= 127;
    }

    private void take(char expectedChar) {
        if(currChar == expectedChar) {
            currSpelling.append(currChar);
            readChar();
        }
        else {
            scanError("Unexpected character '" + currChar + "' in input");
        }
    }

    private void takeIt() {
        currSpelling.append(currChar);
        readChar();
    }

    private void readChar(){
        if(eot) return;
        try {
            int c = inputStream.read();
            if(c == -1)  {
                eot = true;
                currChar = '$';
            }
            else {
                currChar = (char)c;
                if(currChar=='\n') currLine += 1;
            }
        }
        catch(IOException e) {
            scanError("I/O Exception!");
            eot = true;
        }
        if(!isASCii(currChar)) scanError("'" + currChar + "' is not ASCII");
    }

    private void scanError(String m) {
        reporter.reportError("Scan Error:  " + m);
    }

    public static void main(String[] args) {
        //String filepath = args[0];
        String filepath = "/users/andrew/IdeaProjects/Tests/pa1_tests/fail130.java";
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(filepath);
        } catch (FileNotFoundException e) {
            System.out.println("Input file "+filepath+" not found");
            System.exit(3);
        }

        ErrorReporter errorReporter = new ErrorReporter();
        Scanner scanner = new Scanner(inputStream, errorReporter);
        Token token = scanner.scan();
        System.out.println(token.kind+": "+token.spelling+" "+token.posn);
        while(token.kind != TokenKind.EOT){
            token = scanner.scan();
            System.out.println(token.kind+": "+token.spelling+" "+token.posn);
        }
    }
}