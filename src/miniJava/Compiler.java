package miniJava;

import miniJava.AbstractSyntaxTrees.AST;
import miniJava.AbstractSyntaxTrees.ASTDisplay;
import miniJava.CodeGeneration.Encoder;
import miniJava.ContextualAnalyzer.IdChecker;
import miniJava.ContextualAnalyzer.TypeChecker;
import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;
import miniJava.mJAM.ObjectFile;

import java.io.*;

public class Compiler {
    public static void main(String[] args){
        //String filepath = args[0];
        //String filepath = "/users/andrew/IdeaProjects/Tests/pa3_tests/fail394.java";
        String filepath = "/users/andrew/desktop/test.java";
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(filepath);
        } catch (FileNotFoundException e) {
            System.out.println("Input file "+filepath+" not found");
            System.exit(3);
        }

        ErrorReporter errorReporter = new ErrorReporter();
        Scanner scanner = new Scanner(inputStream, errorReporter);
        Parser parser = new Parser(scanner, errorReporter);

        System.out.println("Syntactic analysis...");
        AST ast = parser.parse();
        if (errorReporter.hasErrors()) {
            System.out.println("Invalid miniJava program");
            System.exit(4);
        }
        //ASTDisplay v = new ASTDisplay();
        //v.showTree(ast);

        System.out.println("Contextual analysis - ID checking...");
        IdChecker idChecker = new IdChecker(errorReporter);
        idChecker.check(ast);
        if (errorReporter.hasErrors()) {
            System.out.println("Invalid miniJava program");
            System.exit(4);
        }

        System.out.println("Contextual analysis - Type checking...");
        TypeChecker typeChecker = new TypeChecker(errorReporter);
        typeChecker.check(ast);
        if (errorReporter.hasErrors()) {
            System.out.println("Invalid miniJava program");
            System.exit(4);
        }

        System.out.println("Encoding abstract syntax tree...");
        Encoder encoder = new Encoder(errorReporter);
        encoder.encode(ast);
        if (errorReporter.hasErrors()) {
            System.out.println("Invalid miniJava program");
            System.exit(4);
        }

        System.out.println("Generating object file...");
        ObjectFile of = new ObjectFile("/users/andrew/desktop/test.mJAM");
        of.write();
        System.out.println("Valid miniJava program");
        System.exit(0);
    }
}
