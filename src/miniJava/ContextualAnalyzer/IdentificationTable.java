package miniJava.ContextualAnalyzer;

import miniJava.AbstractSyntaxTrees.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class IdentificationTable {
    private List<HashMap<String, Declaration>> table;
    private int currLevel;
    public IdentificationTable() {
        table = new LinkedList<>();
        table.add(new HashMap<>());
        currLevel = 0;
    }

    public void openScope() {
        table.add(new HashMap<>());
        currLevel += 1;
    }

    public void closeScope() {
        if(currLevel >= 0) {
            table.remove(currLevel);
            currLevel -= 1;
        }
    }

    public boolean enter(String id, Declaration decl) {
        if (table.get(currLevel).containsKey(id)){
            return false;
        }
        table.get(currLevel).put(id, decl);
        return true;
    }

    public Declaration retrieve(String id) {
        for(int i=currLevel; i>=0; i--) {
            if(table.get(i).containsKey(id)) return table.get(i).get(id);
        }
        return null;
    }

    public Declaration retrieveClass(String id) {
        for(int i=currLevel; i>=0; i--) {
            if(table.get(i).containsKey(id)){
                Declaration cd = table.get(i).get(id);
                if (cd instanceof ClassDecl) return table.get(i).get(id);
            }
        }
        return null;
    }

    public Declaration retrieveMethod(String id) {
        for(int i=currLevel; i>=0; i--) {
            if(table.get(i).containsKey(id)){
                Declaration cd = table.get(i).get(id);
                if (cd instanceof MethodDecl) return table.get(i).get(id);
            }
        }
        return null;
    }

    public Declaration retrieveFPV(String id) {
        for(int i=currLevel; i>=0; i--) {
            if(table.get(i).containsKey(id)){
                Declaration cd = table.get(i).get(id);
                if (cd instanceof FieldDecl || cd instanceof ParameterDecl || cd instanceof VarDecl) return table.get(i).get(id);
            }
        }
        return null;
    }
}
