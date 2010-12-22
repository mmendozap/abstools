package abs.frontend.typechecker.locationtypes.infer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import abs.common.FileUtils;
import abs.common.WrongProgramArgumentException;
import abs.frontend.ast.ASTNode;
import abs.frontend.ast.CompilationUnit;
import abs.frontend.ast.Decl;
import abs.frontend.ast.FieldDecl;
import abs.frontend.ast.FunctionDecl;
import abs.frontend.ast.MethodSig;
import abs.frontend.ast.Model;
import abs.frontend.ast.ParamDecl;
import abs.frontend.ast.VarDecl;
import abs.frontend.parser.Main;
import abs.frontend.typechecker.locationtypes.LocationType;

public class InferMain extends Main {
    
    enum Config {
        INTERFACES,
        CLASSES,
        LOCAL_VAR_DECLS,
        FIELDS,
        FUNCTIONS
    }
    
    EnumSet<Config> config = EnumSet.of(Config.INTERFACES, Config.CLASSES);
    
    public static void main(final String... args) {
        InferMain m = new InferMain();
        try {
            m.compile(args);
        } catch (WrongProgramArgumentException pe) {
            System.err.println(pe.getMessage());
            m.printUsageAndExit();
        }  catch (Exception e) {
            System.err.println("An error occurred during compilation: " + e.getMessage());
            e.printStackTrace();

            System.exit(1);
        }
    }

    private File destDir = new File(".");

    @Override
    public List<String> parseArgs(String[] args) throws Exception {
        List<String> restArgs = super.parseArgs(args);
        List<String> remainingArgs = new ArrayList<String>();

        for (int i = 0; i < restArgs.size(); i++) {
            String arg = restArgs.get(i);
            if (arg.equals("-d")) {
                i++;
                if (i == restArgs.size()) {
                    new WrongProgramArgumentException("Please provide a destination directory");
                } else {
                    destDir = new File(args[i]);
                }
            } if (arg.startsWith("-locinferwritebackscope=")) {
                String[] s = arg.split("=");
                if (s.length < 2) {
                    new WrongProgramArgumentException("Please provide a scope");
                } else {
                    readScopeArg(s[1]);
                }
            } else {
                remainingArgs.add(arg);
            }
        }

        return remainingArgs;
    }

    private void readScopeArg(String scope) throws WrongProgramArgumentException {
        String[] scopes = scope.split(",");
        config = EnumSet.noneOf(Config.class);
        for (String s : scopes) {
            if (s.equals("all")) {
                config = EnumSet.allOf(Config.class);
            } else {
                try {
                    Config c = Config.valueOf(s.toUpperCase());
                    config.add(c);
                } catch (IllegalArgumentException e) {
                    throw new WrongProgramArgumentException("Unkown scope "+scope);
                }
            }
        }
    }

    protected void printUsage() {
        super.printUsage();
        System.out.println("Location Type Inferrer:");
        System.out.println("  -d <dir>     generate files to <dir>");
        System.out.println("  -locinferwritebackscope=<scope>,<scope>,... "); 
        System.out.println("		   only write back location type inference results");
        System.out.println("               to given scopes. Where <scope> can be one of ");
        System.out.println("               interfaces, classes, fields, functions, and all \n");
    }

    private void compile(String[] args) throws Exception {
        locationTypeInferenceEnabled = true;
        final Model model = parse(args);
        if (model.hasParserErrors() || model.hasErrors() || model.hasTypeErrors())
            return;

        if (!destDir.exists()) {
            System.err.println("Destination directory " + destDir.getAbsolutePath() + " does not exist!");
            System.exit(1);
        }

        if (!destDir.canWrite()) {
            System.err.println("Destination directory " + destDir.getAbsolutePath() + " cannot be written to!");
            System.exit(1);
        }

        LocationTypeInferrerExtension ltie = (LocationTypeInferrerExtension)model.getTypeExt().getFirstRegisteredTypeExtension(LocationTypeInferrerExtension.class);
        writeInferenceResultsBack(ltie.getResults());

    }
    
    public void setConfig(Config... configs) {
        this.config = EnumSet.copyOf(Arrays.asList(configs));
    }
    
    public void writeInferenceResultsBack(Map<LocationTypeVariable, LocationType> results) throws IOException {
        Map<CompilationUnit, List<LocationTypeVariable>> m = clusterByCompilationUnit(results);
        
        for (Entry<CompilationUnit, List<LocationTypeVariable>> e : m.entrySet()) {
            CompilationUnit cu = e.getKey();
            List<LocationTypeVariable> l = getSortedList(e);
            File file = new File(cu.getFileName());
            StringBuilder sb = FileUtils.fileToStringBuilder(file);
            int offset = 0;
            for (LocationTypeVariable ltv : l) {
                if (shouldBeConsidered(ltv)) {
                    int pos = offset + ltv.getTypeNode().getAbsolutePosition();
                    String s = results.get(ltv).toAnnoString();
                    sb.insert(pos, s);
                    offset += s.length();
                }
            }
            FileUtils.writeStringBuilderToFile(sb, file);
        }
    }

    private List<LocationTypeVariable> getSortedList(Entry<CompilationUnit, List<LocationTypeVariable>> e) {
        List<LocationTypeVariable> l = e.getValue();
        Collections.sort(l, new Comparator<LocationTypeVariable>() {
            @Override
            public int compare(LocationTypeVariable o1, LocationTypeVariable o2) {
                int pos1 = o1.getTypeNode().getAbsolutePosition();
                int pos2 = o2.getTypeNode().getAbsolutePosition();
                if (pos1 == -1 || pos2 == -1) {
                    throw new RuntimeException("Absolute position not defined");
                }
                return Integer.valueOf(pos1).compareTo(pos2);
            }
        });
        return l;
    }

    private Map<CompilationUnit, List<LocationTypeVariable>> clusterByCompilationUnit(
            Map<LocationTypeVariable, LocationType> results) {
        Map<CompilationUnit, List<LocationTypeVariable>> m = new HashMap<CompilationUnit, List<LocationTypeVariable>>();
        for (LocationTypeVariable ltv : results.keySet()) {
            ASTNode<?> node = ltv.getNode();
            if (node == null) continue;
            CompilationUnit cu = node.getCompilationUnit();
            if (cu.getName().equals("ABS.StdLib")) continue;
            List<LocationTypeVariable> list = m.get(cu);
            if (list == null) {
                list = new ArrayList<LocationTypeVariable>();
                m.put(cu, list);
            }
            list.add(ltv);
        }
        return m;
    }

    private boolean shouldBeConsidered(LocationTypeVariable ltv) {
        ASTNode<?> node = ltv.getNode();
        Decl contextDecl = node.getContextDecl();
        
        if (contextDecl != null) {
            if (contextDecl.isClass() && !config.contains(Config.CLASSES))
                return false;
        
            if (contextDecl.isInterface() && !config.contains(Config.INTERFACES))
                return false;

            if (contextDecl.isFunction() && !config.contains(Config.FUNCTIONS))
                return false;
        }
        
        if (node instanceof VarDecl && !config.contains(Config.LOCAL_VAR_DECLS)) 
            return false;
        
        if (node instanceof FieldDecl && !config.contains(Config.FIELDS)) 
            return false;

        
        return true;
    }

}
