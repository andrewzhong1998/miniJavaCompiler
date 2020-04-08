package miniJava.SyntacticAnalyzer;

public class SourcePosition {

    public int start;
    public int finish;

    public SourcePosition () {
        start = 0;
        finish = 0;
    }

    public SourcePosition (int start, int finish) {
        this.start = start;
        this.finish = finish;
    }

    public SourcePosition (SourcePosition posn) {
        this.start = posn.start;
        this.finish = posn.finish;
    }

    public String toString() {
        return "(" + start + ", " + finish + ")";
    }
}
