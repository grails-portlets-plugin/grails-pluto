import grails.util.GrailsUtil
import groovy.xml.StreamingMarkupBuilder
import org.mortbay.jetty.Server
import org.mortbay.jetty.security.HashUserRealm
import org.mortbay.jetty.security.UserRealm
import org.mortbay.jetty.plus.jaas.JAASUserRealm
import org.mortbay.jetty.servlet.SessionHandler
import org.mortbay.jetty.webapp.WebAppContext
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

// default portlet spec to 1.0
def portletVersion = '1.0'
def plutoVersion = '1.1.7'

def pluginLibDir = "${plutoPluginDir}/lib"

eventConfigureJetty = {Server server ->

    try {
        SessionHandler sh = new SessionHandler();
        //kindly borrowed from the Maven pluto plugin author Nils-Helge Garli
        def sessionMan = classLoader.loadClass('com.bekk.boss.pluto.embedded.jetty.util.PlutoJettySessionManager')
        sh.setSessionManager(sessionMan.newInstance());
        server.getHandler().setSessionHandler(sh);
    } catch (NoClassDefFoundError e) {
        // This script is compiled before the plugin source in some cases so we need to ignore compile errors as the classs wil be there at runtime when it's needed
    }

    // TODO refactor pluto specific code out to pluggable embedded portal interface
    def webContext = new WebAppContext("${pluginLibDir}/pluto-portal-${plutoVersion}", "pluto")
    webContext.systemClasses = ["-org.apache.pluto.driver.", "org.apache.pluto.", "javax.portlet.", "javax.servlet.", "org.springframework."]
    webContext.contextPath = "/pluto"
    try {
        sh = new SessionHandler();
        def sessionMan = classLoader.loadClass('com.bekk.boss.pluto.embedded.jetty.util.PlutoJettySessionManager')
        sh.setSessionManager(sessionMan.newInstance());
        webContext.setSessionHandler(sh);
    } catch (NoClassDefFoundError e) {
        // This script is compiled before the plugin source in some cases so we need to ignore compile errors as the classs wil be there at runtime when it's needed
    }
    server.addHandler(webContext)
    HashUserRealm myrealm = new HashUserRealm("default", "${pluginLibDir}/realm.properties");
    server.setUserRealms([myrealm] as UserRealm[]);

}

eventSetClasspath = {
    if (config?.portlet?.version == '2') {
        portletVersion = '2.0'
        plutoVersion = '2.0.0-SNAPSHOT'
    }
    event("StatusUpdate", ["Using Portlet Spec ${portletVersion}"])

    def jars = ["${pluginLibDir}/runtime/castor-1.1.1.jar",
            "${pluginLibDir}/runtime/pluto-container-${plutoVersion}.jar",
            "${pluginLibDir}/runtime/pluto-descriptor-api-${plutoVersion}.jar",
            "${pluginLibDir}/runtime/pluto-descriptor-impl-${plutoVersion}.jar",
            "${pluginLibDir}/runtime/pluto-taglib-${plutoVersion}.jar",
            "${pluginLibDir}/runtime/portlet-api-${portletVersion}.jar"
            ]
    if (portletVersion == '2.0') {
        jars += ["${pluginLibDir}/runtime/ccpp-1.0.jar",
                "${pluginLibDir}/runtime/jaxb-api-2.1.jar",
                "${pluginLibDir}/runtime/activation-1.1.jar",
                "${pluginLibDir}/runtime/stax-api-1.0-2.jar",
                "${pluginLibDir}/runtime/jaxb-impl-2.1.3.jar"
                ]
    }
    
    jars.each {jar ->
        File file = new File(jar)
        if (!file.exists()) {
            throw new RuntimeException("Unable to find Portlets lib: $jar")
        }
        rootLoader.addURL(file.toURI().toURL());
    }
}


eventPackagingEnd = {
    def plutoConfigXml = new File("${plutoPluginDir}/lib/pluto-portal-${plutoVersion}/WEB-INF/pluto-portal-driver-config.xml")
    try {
        def xmlWriter = new StreamingMarkupBuilder();
        def searchPath = "file:${basedir}/grails-app/portlets/**/*Portlet.groovy"
        def customModes = [:]
        def userAttributes = [:]
        event("StatusUpdate", ["Searching for portlets: ${searchPath}"])
        portletFiles = resolveResources(searchPath).toList()
        if (portletFiles.size() > 0) {
            event("StatusUpdate", ["Generating pluto-portal-driver-config.xml - ${portletFiles.size()} portlets found"])

            if (GrailsUtil.environment == 'development' || GrailsUtil.environment == 'test') {
                // TODO refactor pluto specific code out to pluggable embedded portal interface
                sw = new StringWriter()
                xmlWriter = new StreamingMarkupBuilder()
                if (plutoConfigXml.exists()) plutoConfigXml.delete()
                xml = xmlWriter.bind {
                    'pluto-portal-driver'(
                            'xmlns': "http://portals.apache.org/pluto/xsd/pluto-portal-driver-config.xsd",
                            'xmlns:xsi': "http://www.w3.org/2001/XMLSchema-instance",
                            'xsi:schemaLocation': "http://portals.apache.org/pluto/xsd/pluto-portal-driver-config.xsd",
                            'version': "1.1") {
                        'portal-name'('pluto-portal-driver')
                        'portal-version'(plutoVersion)
                        'container-name'('Pluto Portal Driver')
                        'supports'
                        {
                            'portlet-mode'('view')
                            'portlet-mode'('edit')
                            'portlet-mode'('help')
                            'portlet-mode'('config')

                            'window-state'('normal')
                            'window-state'('maximized')
                            'window-state'('minimized')
                        }
                        'render-config'(default: 'Home') {
                            'page'(name: "Home", uri: "/WEB-INF/themes/pluto-default-theme.jsp") {
                                portletFiles.each {portletClassFile ->
                                    def className = portletClassFile.filename - '.groovy'
                                    def portletName = className - 'Portlet'
                                    'portlet'(context: "/${grailsAppName}", name: portletName)
                                }
                            }
                            'page'(name: "About Apache Pluto", uri: "/WEB-INF/themes/pluto-default-theme.jsp") {
                                'portlet'(context: '/pluto', name: 'AboutPortlet')
                            }
                            'page'(name: "Pluto Admin", uri: "/WEB-INF/themes/pluto-default-theme.jsp") {
                                'portlet'(context: '/pluto', name: 'PlutoPageAdmin')
                            }

                        }
                    }
                }
                plutoConfigXml.write(xml.toString())
            }
        }
    } catch (Exception e) {
        event("StatusError", ["Unable to generate pluto-portal-driver-config.xml: " + e.message])
        exit(1)
    }
}

// Those jars are loaded at parents classloaders, 
// and causes silent portlet deployment failure if included.
eventCreateWarStart = { warName, stagingDir ->
    ant.delete {
      fileset(dir:"${stagingDir}/WEB-INF/lib") {
       include(name: "servlet-api*.jar")
       include(name: "portlet-api*.jar")
       include(name: "jcl-over-slf4j*.jar")
      }
   }
}

eventStatusFinal = { message ->
    if(message.startsWith("Server running.")) {
        def myMessage = "Pluto is running. To access portlets, browse to scheme://hostname:port/pluto"
        event("StatusFinal", [myMessage])
    }
}

def resolveResources(String pattern) {
    def resolver = new PathMatchingResourcePatternResolver()
    return resolver.getResources(pattern)
}

