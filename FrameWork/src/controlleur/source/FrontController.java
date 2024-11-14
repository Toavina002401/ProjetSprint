package controlleur.source;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import controlleur.annotation.AnnotationAttribut;
import controlleur.annotation.AnnotationControlleur;
import controlleur.annotation.AnnotationObject;
import controlleur.annotation.Param;
import controlleur.annotation.Post;
import controlleur.annotation.Url;
import controlleur.fonction.ModelView;
import controlleur.fonction.Reflection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

@MultipartConfig
public class FrontController extends HttpServlet {
    private String controllerPackage;
    private Gson gson = new Gson();
    private HashMap<String, Mapping> liste = new HashMap<String, Mapping>();
    private Exception errorPackage = new Exception("null");
    private Exception errorLien = new Exception("null");
    private String statusCode = "200";

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public Exception getErrorLien() {
        return errorLien;
    }

    public void setErrorLien(Exception errorLien) {
        this.errorLien = errorLien;
    }

    public Exception getErrorPackage() {
        return errorPackage;
    }

    public void setErrorPackage(Exception errorPackage) {
        this.errorPackage = errorPackage;
    }

    public HashMap<String, Mapping> getListe() {
        return liste;
    }

    public void setListe(HashMap<String, Mapping> liste) {
        this.liste = liste;
    }

    @Override
    public void init() throws ServletException {
        this.setControllerPackage(getServletConfig().getInitParameter("namePath"));
        this.scan(getServletContext());
        super.init();
    }

    public String getControllerPackage() {
        return controllerPackage;
    }

    public void setControllerPackage(String controllerPackage) {
        this.controllerPackage = controllerPackage;
    }

    public void scan(ServletContext context) {
        HashMap<String, Mapping> boite = new HashMap<>();
        String packageName = this.getControllerPackage();
        try {
            String classesPath = context.getRealPath("/WEB-INF/classes");
            String decodedPath = URLDecoder.decode(classesPath, "UTF-8");
            String packagePath = decodedPath + "/" + packageName.replace('.', '/');
            File packageDirectory = new File(packagePath);
            if (packageDirectory.exists() && packageDirectory.isDirectory()) {
                File[] classFiles = packageDirectory.listFiles((dir, name) -> name.endsWith(".class"));
                if (classFiles != null) {
                    for (File file : classFiles) {
                        String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                        Class<?> clazz = Class.forName(className);
                        if (isController(clazz)) {
                            Method[] listeMethod = clazz.getDeclaredMethods();
                            for (Method method : listeMethod) {
                                Mapping map = new Mapping();
                                map.setClassName(className);
                                String verbe = "GET";
                                if (method.isAnnotationPresent(Url.class)) {
                                    String key = method.getAnnotation(Url.class).value();
                                    if (method.isAnnotationPresent(Post.class)) {
                                        verbe = "POST";
                                    }
                                    VerbAction verbeAction = new VerbAction(method.getName(), verbe);
                                    if (boite.containsKey(key)) {
                                        Mapping keyExist = boite.get(key);
                                        if (keyExist.contains(verbeAction)) {
                                            this.setStatusCode("409");
                                            throw new Exception("Erreur : Deux URL qui sont pareil sur cette lien "
                                                    + key + " avec le meme verbe " + verbe);
                                        }
                                        keyExist.addVerbAction(verbeAction);
                                    } else {
                                        map.addVerbAction(verbeAction);
                                        boite.put(key, map);
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                this.setStatusCode("500");
                this.setErrorPackage(new Exception("Erreur Package non existant " + packageName));
            }
        } catch (Exception e) {
            this.setErrorLien(e);
        }
        this.setListe(boite);
    }

    private static boolean isController(Class<?> clazz) {
        return clazz.isAnnotationPresent(AnnotationControlleur.class);
    }

    private String traitement(String description, HttpServletRequest req, HttpServletResponse res) throws Exception {
        String url = req.getRequestURI();
        String nameProjet = req.getContextPath();
        int test = 0;
        for (Map.Entry<String, Mapping> entry : this.getListe().entrySet()) {
            String key = nameProjet + entry.getKey();
            Mapping value = entry.getValue();
            String verbe = req.getMethod();
            if (key.equals(url)) {
                int idVerbeMethode = 0;
                for (int i = 0; i < value.getVerbeAction().size(); i++) {
                    if (value.getVerbeAction().get(i).getVerb().equals(verbe)) {
                        idVerbeMethode = i;
                    }
                }
                test++;
                try {
                    if (!verbe.equals(value.getVerbeAction().get(idVerbeMethode).getVerb())) {
                        this.setStatusCode("405");
                        throw new Exception("La methode " + value.getVerbeAction().get(idVerbeMethode).getMethodName()
                                + " est invoquee en " + value.getVerbeAction().get(idVerbeMethode).getVerb()
                                + " alors que ton formulaire opte pour du " + verbe
                                + " . Un petit ajustement s'impose");
                    }
                    Class<?> obj = Class.forName(value.getClassName());
                    Object objInstance = obj.getDeclaredConstructor().newInstance();
                    Field[] fields = obj.getDeclaredFields();
                    for (Field field : fields) {
                        if (field.getType().equals(CustomeSession.class)) {
                            CustomeSession customSession = new CustomeSession();
                            customSession.setSession(req.getSession());
                            field.setAccessible(true);
                            field.set(objInstance, customSession);
                        }
                    }

                    if (Reflection.findParam(objInstance, value.getVerbeAction().get(idVerbeMethode).getMethodName())) {
                        Parameter[] objParametre = Reflection.getParam(objInstance,value.getVerbeAction().get(idVerbeMethode).getMethodName());
                        Object[] objValeur = new Object[objParametre.length];
                        Enumeration<String> reqParametre = req.getParameterNames();
                        Enumeration<String> reqParametre2 = req.getParameterNames();
                        boolean isSession = false;
                        int idParamSession = 0;
                        for (int i = 0; i < objParametre.length; i++) {
                            Class<?> objTemp = Reflection.getClassForName(objParametre[i].getParameterizedType().getTypeName());
                            Object objTempInstance = null;
                            
                            if (objParametre[i].getType() == Part.class) {
                                if (objParametre[i].isAnnotationPresent(Param.class)) {
                                    String paramPart = objParametre[i].getAnnotation(Param.class).value();
                                    Part file = req.getPart(paramPart);
                                    objValeur[i] = (Part) file;                              
                                } else {
                                    this.setStatusCode("400");
                                    throw new Exception("ETU002401 il n'y a pas de parametre sur cette methode");
                                }
                            } else {
                                if (!objTemp.isPrimitive()) {
                                    objTempInstance = objTemp.getDeclaredConstructor().newInstance();
                                }
                                if (!objTemp.isPrimitive() && objTempInstance.getClass().isAnnotationPresent(AnnotationObject.class)) {
                                    if (objParametre[i].isAnnotationPresent(Param.class)) {
                                        Field[] lesAttributs = objTempInstance.getClass().getDeclaredFields();
                                        Object[] attributsValeur = new Object[lesAttributs.length];
                                        for (int j = 0; j < lesAttributs.length; j++) {
                                            int verif = 0;
                                            while (reqParametre.hasMoreElements()) {
                                                String paramName = reqParametre.nextElement();

                                                if (!Reflection.validation(lesAttributs[j], req.getParameter(paramName))) {
                                                    this.setStatusCode("401");
                                                    throw new Exception("Erreur du validation :"+Reflection.erreurValidation(lesAttributs[j], req.getParameter(paramName)));
                                                }else{
                                                    if (paramName.startsWith(objParametre[i].getAnnotation(Param.class).value() + ".")) {
                                                        String lastPart = "";
                                                        int lastIndex = paramName.lastIndexOf(".");
                                                        if (lastIndex != -1 && lastIndex != paramName.length() - 1) {
                                                            lastPart = paramName.substring(lastIndex + 1);
                                                        }

                                                        if (lesAttributs[j].getName().compareTo(lastPart) == 0) {
                                                            attributsValeur[j] = Reflection.castParameter(req.getParameter(paramName),lesAttributs[j].getType().getName());
                                                            verif++;
                                                            break;
                                                        }
                                                        if (lesAttributs[j].isAnnotationPresent(AnnotationAttribut.class)) {
                                                            if (lesAttributs[j].getAnnotation(AnnotationAttribut.class).value().compareTo(lastPart) == 0) {
                                                                attributsValeur[j] = Reflection.castParameter(req.getParameter(paramName),lesAttributs[j].getType().getName());
                                                                verif++;
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            if (verif == 0) {
                                                attributsValeur[j] = Reflection.castParameter(null,lesAttributs[j].getType().getName());
                                            }
                                        }
                                        objTempInstance = Reflection.process(objTempInstance, attributsValeur);
                                        objValeur[i] = objTempInstance;
                                    } else {
                                        this.setStatusCode("400");
                                        throw new Exception("ETU002401 il n'y a pas de parametre sur cette methode");
                                    }
                                } else if (objTempInstance.getClass().getTypeName().compareTo("controlleur.source.CustomeSession") == 0) {
                                    isSession = true;
                                    idParamSession = i;
                                    objValeur[i] = Reflection.castParameter(null,
                                            objParametre[i].getParameterizedType().getTypeName());
                                } else {
                                    int verif = 0;
                                    while (reqParametre2.hasMoreElements()) {
                                        String paramName = reqParametre2.nextElement();
                                        if (objParametre[i].isAnnotationPresent(Param.class)) {
                                            if (objParametre[i].getAnnotation(Param.class).value().compareTo(paramName) == 0) {
                                                objValeur[i] = Reflection.castParameter(req.getParameter(paramName),objParametre[i].getParameterizedType().getTypeName());
                                                verif++;
                                                break;
                                            }
                                        } else {
                                            this.setStatusCode("400");
                                            throw new Exception("ETU002401 il n'y a pas de parametre sur cette methode");
                                        }
                                    }
                                    if (verif == 0) {
                                        objValeur[i] = Reflection.castParameter(null,objParametre[i].getParameterizedType().getTypeName());
                                    }
                                }
                            }
                        }

                        if (isSession) {
                            Class<?> objTemp = Reflection.getClassForName(objParametre[idParamSession].getParameterizedType().getTypeName());
                            Object objTempInstance = objTemp.getDeclaredConstructor().newInstance();
                            CustomeSession session = (CustomeSession) objTempInstance;
                            session.setSession(req.getSession());
                            objValeur[idParamSession] = session;
                        }
                        
                        String reponse = Reflection.execMethodeController(objInstance,value.getVerbeAction().get(idVerbeMethode).getMethodName(), objValeur); 
                        
                        if (Reflection.isRestAPI(objInstance,value.getVerbeAction().get(idVerbeMethode).getMethodName())) {
                            if (reponse.compareTo("controlleur.fonction.ModelView") == 0) {
                                ModelView mv = (ModelView) Reflection.execMethode(objInstance,value.getVerbeAction().get(idVerbeMethode).getMethodName(), objValeur);
                                String jsonResponse = gson.toJson(mv.getData());
                                req.setAttribute("baseUrl", nameProjet);
                                description = jsonResponse;
                            } else {
                                String jsonResponse = gson.toJson(reponse);
                                description = jsonResponse;
                            }
                            res.setContentType("text/json");
                        } else {
                            if (reponse.compareTo("controlleur.fonction.ModelView") == 0) {
                                ModelView mv = (ModelView) Reflection.execMethode(objInstance,value.getVerbeAction().get(idVerbeMethode).getMethodName(), objValeur);
                                String cleHash = "";
                                Object valueHash = new Object();
                                for (String cles : mv.getData().keySet()) {
                                    cleHash = cles;
                                    valueHash = mv.getData().get(cles);
                                    req.setAttribute(cleHash, valueHash);
                                }
                                req.setAttribute("baseUrl", nameProjet);
                                req.getServletContext().getRequestDispatcher(mv.getUrl()).forward(req, res);
                            } else {
                                description += reponse;
                            }
                        }
                    } else {
                        String reponse = Reflection.execMethodeController(objInstance,value.getVerbeAction().get(idVerbeMethode).getMethodName(), null);
                        if (Reflection.isRestAPI(objInstance,value.getVerbeAction().get(idVerbeMethode).getMethodName())) {
                            if (reponse.compareTo("controlleur.fonction.ModelView") == 0) {
                                ModelView mv = (ModelView) Reflection.execMethode(objInstance,value.getVerbeAction().get(idVerbeMethode).getMethodName(), null);
                                String jsonResponse = gson.toJson(mv.getData());
                                req.setAttribute("baseUrl", nameProjet);
                                description = jsonResponse;
                            } else {
                                String jsonResponse = gson.toJson(reponse);
                                description = jsonResponse;
                            }
                            res.setContentType("text/json");
                        } else {
                            if (reponse.compareTo("controlleur.fonction.ModelView") == 0) {
                                ModelView mv = (ModelView) Reflection.execMethode(objInstance,value.getVerbeAction().get(idVerbeMethode).getMethodName(), null);
                                String cleHash = "";
                                Object valueHash = new Object();
                                for (String cles : mv.getData().keySet()) {
                                    cleHash = cles;
                                    valueHash = mv.getData().get(cles);
                                    req.setAttribute(cleHash, valueHash);
                                }
                                req.setAttribute("baseUrl", nameProjet);
                                req.getServletContext().getRequestDispatcher(mv.getUrl()).forward(req, res);
                            } else {
                                description += reponse;
                            }
                        }
                    }
                } catch (Exception e) {
                    throw e;
                }
            }
        }
        if (test == 0) {
            this.setStatusCode("404");
            throw new Exception("Lien inexistante : Il n'y a pas de methodes associer a cette chemin " + req.getRequestURL());
        }
        return description;
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        PrintWriter out = res.getWriter();
        String valiny = "";

        if (this.getErrorPackage().getMessage().compareTo("null") == 0
                && this.getErrorLien().getMessage().compareTo("null") == 0) {
            try {
                valiny = traitement(valiny, req, res);
            } catch (Exception e) {
                valiny = e.getMessage();
                res.sendError(Integer.parseInt(this.getStatusCode()), valiny);
            }
        } else {
            if (this.getErrorPackage().getMessage().compareTo("null") == 0) {
                valiny = this.getErrorLien().getMessage();
                res.sendError(Integer.parseInt(this.getStatusCode()), valiny);
            } else {
                valiny = this.getErrorPackage().getMessage();
                res.sendError(Integer.parseInt(this.getStatusCode()), valiny);
            }
        }

        out.println(valiny);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

}
