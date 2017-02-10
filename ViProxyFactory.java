    /*
     *   utils to create wrapper code for a certain class
     */
    static class ViProxyFactory{
        private final static int SYNTHETIC = 0x1000;

        private enum FilterAction{
            E_NULL,
            E_INVERT,
            E_NORMAL,
        };

        private String mOrigClass;
        private String mOrigPkg, mProxyPkg, mProxyClass;
        private Map<String, String> mFilters;
        private String mTag = "vi";

        ViProxyFactory(String origPkg, String proxyPkg, String proxyClass, Map<String, String> filters){
            mOrigPkg = origPkg;
            mProxyPkg = proxyPkg;
            mProxyClass = proxyClass;
            mFilters = filters == null ? new HashMap<String, String>() : filters;
            mOrigClass = origPkg + "." + proxyClass;

            Log.d(mTag, String.format("%s.%s proxy for %s\n", proxyPkg, proxyClass, mOrigClass));
        }

        private String filter(String name, Map<String,String> filters, FilterAction invert){
            //replace with the desired name
            if(filters != null)
            for (Map.Entry<String,String> e : filters.entrySet()) {
                if(invert == FilterAction.E_INVERT){
                    if(name.equals(e.getValue())){
                        return e.getKey();
                    }
                }else if(invert == FilterAction.E_NORMAL){
                    if(name.equals(e.getKey())){
                        return e.getValue();
                    }
                }
            }

            //do some convertion for subclass type if necessary
            return name.replaceAll("\\$", ".");
        }

        private boolean involved(String name){
            for (Map.Entry<String,String> e : mFilters.entrySet()
                    ) {
                if(name.equals(e.getKey())){
                    return true;
                }
            }
            return false;
        }

        private boolean involvedByParameters(Method med){
            Class<?> types[] = med.getParameterTypes();
            for (Class<?> type:types) {
                if(involved(type.getName())){
                    return true;
                }
            }
            return false;
        }

        /*
         * type == 0 stands for "Package access scope"
         */
        private List getMethodsByAccess(Class<?> cls, int type){
            List myMeds = new ArrayList();
            for (Method med:cls.getDeclaredMethods()
                    ) {
                Log.d(mTag, String.format("%s flag=%x\n", med.getName(), med.getModifiers()));
                if((med.getModifiers() & SYNTHETIC) != 0){
                    //skip compiler created methods
                    continue;
                }else if((med.getModifiers() & type) != 0){
                    myMeds.add(med);
                }else if(type==0 && (med.getModifiers() & (Modifier.PUBLIC|Modifier.PROTECTED|Modifier.PRIVATE))==0){
                    myMeds.add(med);
                }
            }
            return myMeds;
        }

        private String getMethodAccessName(Method med){
            int flag = med.getModifiers();
            if(Modifier.isPublic(flag)){
                return "public ";
            }else if(Modifier.isProtected(flag)){
                return "protect ";
            }else if(Modifier.isPrivate(flag)){
                return "private ";
            }
            return " ";
        }

        private String constructMethod(Method med, boolean isOverride){
            FilterAction typedef;
            FilterAction typecast;
            String annotationFlag;
            String caller;
            if(isOverride){
                typedef = FilterAction.E_NULL;
                typecast = FilterAction.E_NORMAL;
                annotationFlag = "@Override\n";
                caller = "this";
            }else{
                typedef = FilterAction.E_NORMAL;
                typecast = FilterAction.E_NULL;
                annotationFlag = "";
                caller = "super";
            }

            int para;
            Class<?> types[] = med.getParameterTypes();
            String viMed = "\n";

            //construct method header
            viMed += annotationFlag;
            String accessFlag = getMethodAccessName(med);
            viMed += accessFlag + med.getReturnType().getName() + " ";
            viMed += med.getName() + "(";
            para = 0;
            for (Class<?> type:types) {
                String prefix = viMed.endsWith("(") ? "" : ", ";
                viMed += prefix + filter(type.getName(), mFilters, typedef) + " " + "p_" + para++;
            }
            viMed += ")";

            //construct method's body
            viMed += "{ ";
            if(!med.getReturnType().getName().equals("void")){
                viMed += "return ";
            }
            viMed += String.format(" %s.%s(", caller, med.getName());
            para = 0;
            for (Class<?> type:types) {
                String prefix = viMed.endsWith("(") ? "" : ", ";
                String cast = typecast==FilterAction.E_NULL ? "" : String.format("(%s)", filter(type.getName(), mFilters, typecast));
                viMed += prefix + cast + "p_" + para++;
            }
            viMed += "); }";

            return viMed;
        }

        private String constructMethods(List list){
            String viFile = "";

            for (Object i:list) {
                Method med = (Method)i;
                boolean isNeeded = involvedByParameters(med);

                //append to file
                if(isNeeded){
                    String viMed = constructMethod(med, false);
                    viFile += viMed;
                    viMed = constructMethod(med, true);
                    viFile += viMed;
                }else{
                    Log.d(mTag, "skip method "+med.getName());
                }
            }

            return viFile;
        }

        public String create(){
            //find all public methods and all declared methods
            Class<?> cls;
            try {
                cls = Class.forName(mOrigClass);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                return "";
            }

            //class header
            String viFile = " \npackage " + mProxyPkg + ";\n" +
                    "public class " + mProxyClass + " extends " + mOrigPkg + "." + mProxyClass + " {\n";

            //construct every method
            List list = getMethodsByAccess(cls, Modifier.PUBLIC|Modifier.PROTECTED);
            viFile += constructMethods(list);
            list = getMethodsByAccess(cls, 0);
            viFile += constructMethods(list);

            //class tailer
            viFile +=                "\n}\n";

            return viFile;
        }

    }

    @Test
    public void createProxyCode(){
        Map<String, String> filter = new HashMap<String, String>();
        ViProxyFactory proxy;
        String content;
        filter.put("android.webkit.WebView", "WebView");
//        proxy = new ViProxyFactory("android.webkit", "com.vi.webkit", "WebChromeClient", filter);
//        content = proxy.create();
//        Log.e("vi", content);
//
//        proxy = new ViProxyFactory("android.webkit", "com.vi.webkit", "WebViewClient", filter);
//        content = proxy.create();
//        Log.e("vi", content);

        proxy = new ViProxyFactory("com.vi.webkit", "com.vi.webkit", "WebView", null);
        content = proxy.create();
        Log.e("vi", content);
    }


