    /*
     *   utils to create wrapper code for a certain class
     */
    class ViProxyFactory{
        private final static int SYNTHETIC = 0x1000;

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

        private String filter(String name){
            //replace with the desired name
            for (Map.Entry<String,String> e : mFilters.entrySet()
                 ) {
                if(name.equals(e.getKey())){
                    return e.getValue();
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

        private String constructMethods(List list){
            String viFile = "";

            for (Object i:list) {
                int para;
                Method pubMed = (Method)i;
                Class<?> types[] = pubMed.getParameterTypes();
                String viMed = "\n";
                boolean isNeeded = false;

                //construct method header
                String accessFlag = getMethodAccessName(pubMed);
                viMed += accessFlag + pubMed.getReturnType().getName() + " ";
                viMed += pubMed.getName() + "(";
                para = 0;
                for (Class<?> type:types
                        ) {
                    String prefix = viMed.endsWith("(") ? "" : ", ";
                    viMed += prefix + filter(type.getName()) + " " + "p_" + para++;
                    if(involved(type.getName())){
                        isNeeded = true;
                    }
                }
                viMed += ")";

                //construct method's body
                viMed += "{ ";
                if(!pubMed.getReturnType().getName().equals("void")){
                    viMed += "return ";
                }
                viMed += " super." + pubMed.getName() + "(";
                para = 0;
                for (Class<?> type:types
                        ) {
                    String prefix = viMed.endsWith("(") ? "" : ", ";
                    viMed += prefix + "p_" + para++;
                }
                viMed += "); }";

                //append to file
                if(isNeeded){
                    viFile += viMed;
                }else{
                    Log.d(mTag, "skip method "+pubMed.getName());
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


