import grails.util.GrailsUtil
import org.codehaus.grails.portlets.*
import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.plugins.PluginMetaManager
import org.codehaus.groovy.grails.plugins.web.ControllersGrailsPlugin
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import org.codehaus.groovy.grails.web.plugins.support.WebMetaUtils
import org.springframework.aop.framework.ProxyFactoryBean
import org.springframework.aop.target.HotSwappableTargetSource
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.core.io.Resource
import org.springframework.web.context.request.RequestContextHolder as RCH

class PlutoGrailsPlugin {
    // the plugin version
    def version = "0.2"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.1 > *"
    // the other plugins this plugin depends on
    def dependsOn = [portlets:"0.3 > * ", portletsPluto:"0.1 > * "]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def author = "Kenji Nakamura"
    def authorEmail = "kenji_nakamura@diva-america.com"
    def title = "Pluto runtime"
    def description = '''\\
Launch Apache Pluto portal server with "run-app" command and deploy portlets
defined in the project.
'''

    def watchedResources = ['file:./grails-app/portlets/**/*Portlet.groovy',
            'file:./plugins/*/grails-app/portlets/**/*Portlet.groovy'
    ]

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugins/pluto"

    def doWithSpring = {
    }

    def doWithApplicationContext = { applicationContext ->
    }

    def doWithWebDescriptor = {webXml ->
        if (GrailsUtil.isDevelopmentEnv() && watchedResources.length > 0) {
            log.info("Creating Pluto servlets for ${watchedResources.length} portlets...")
            for (Resource portlet in watchedResources) {
                def portletName = portlet.filename - 'Portlet.groovy'
                servletElement + {
                    'servlet'
                    {
                        'servlet-name'(portletName)
                        'servlet-class'('org.apache.pluto.core.PortletServlet')
                        'init-param'
                        {
                            'param-name'('portlet-name')
                            'param-value'(portletName)
                        }
                        'load-on-startup'('1')
                    }
                }
                mappingElement + {
                    'servlet-mapping'
                    {
                        'servlet-name'(portletName)
                        'url-pattern'("/PlutoInvoker/${portletName}")
                    }
                }
            }
        }
    }
}
