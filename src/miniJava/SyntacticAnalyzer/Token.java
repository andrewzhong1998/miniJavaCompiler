package miniJava.SyntacticAnalyzer;


public class Token {
    public String spelling;
    public TokenKind kind;
    public SourcePosition posn;

    public Token(TokenKind kind, String spelling, SourcePosition posn){
        this.spelling = spelling;
        this.kind = kind;
        this.posn = posn;

        String[] reserved_string = {"class", "void", "public", "private", "static", "int", "boolean", "this", "return", "if", "else", "while", "true", "false", "new","null"};

        TokenKind[] reserved_kind = {TokenKind.CLASS, TokenKind.VOID, TokenKind.PUBLIC, TokenKind.PRIVATE, TokenKind.STATIC, TokenKind.INT, TokenKind.BOOLEAN,
                TokenKind.THIS, TokenKind.RETURN, TokenKind.IF, TokenKind.ELSE, TokenKind.WHILE, TokenKind.TRUE, TokenKind.FALSE, TokenKind.NEW, TokenKind.NULL};

        for(int i=0; i<reserved_kind.length; i++) {
            if(spelling.equals(reserved_string[i])) {
                this.kind = reserved_kind[i];
                return;
            }
        }

    }

}
