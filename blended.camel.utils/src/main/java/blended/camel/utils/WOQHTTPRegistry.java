package blended.camel.utils;

import org.apache.camel.component.servlet.HttpRegistry;
import org.apache.camel.http.common.CamelServlet;
import org.apache.camel.http.common.HttpConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WOQHTTPRegistry implements HttpRegistry {
  private static final transient Logger LOG = LoggerFactory.getLogger(WOQHTTPRegistry.class);

  private static HttpRegistry singleton;

  private final Set<HttpConsumer> consumers;
  private final Set<CamelServlet> providers;

  public WOQHTTPRegistry() {
    consumers = new HashSet<HttpConsumer>();
    providers = new HashSet<CamelServlet>();
  }

  /**
   * Lookup or create a HttpRegistry
   */
  public static synchronized HttpRegistry getSingletonHttpRegistry() {
    if (singleton == null) {
      singleton = new WOQHTTPRegistry();
    }
    return singleton;
  }

  @Override
  public void register(HttpConsumer consumer) {
    LOG.debug("Registering consumer for path {} providers present: {}",
     consumer.getPath(), providers.size());
    consumers.add(consumer);
    for (CamelServlet provider : providers) {
      provider.connect(consumer);
    }
  }

  @Override
  public void unregister(HttpConsumer consumer) {
    LOG.debug("Unregistering consumer for path {} ", consumer.getPath());
    consumers.remove(consumer);
    for (CamelServlet provider : providers) {
      provider.disconnect(consumer);
    }
  }

  @SuppressWarnings("rawtypes")
  public void register(CamelServletProvider provider, Map properties) {
    LOG.info("Registering provider through OSGi service listener {}", properties);
    try {
      CamelServlet camelServlet = provider.getCamelServlet();
      camelServlet.setServletName((String) properties.get("servlet-name"));
      register(camelServlet);
    } catch (ClassCastException cce) {
      LOG.info("Provider is not a Camel Servlet");
    }
  }

  public void unregister(CamelServletProvider provider, Map<String, Object> properties) {
    LOG.info("Deregistering provider through OSGi service listener {}", properties);
    try {
      CamelServlet camelServlet = provider.getCamelServlet();
      unregister((CamelServlet)provider);
    } catch (ClassCastException cce) {
      LOG.info("Provider is not a Camel Servlet");
    }
  }

  @Override
  public void register(CamelServlet provider) {
    LOG.debug("Registering CamelServlet with name {} consumers present: {}",
     provider.getServletName(), consumers.size());
    providers.add(provider);
    for (HttpConsumer consumer : consumers) {
      provider.connect(consumer);
    }
  }

  @Override
  public void unregister(CamelServlet provider) {
    providers.remove(provider);
  }

  public void setServlets(List<Servlet> servlets) {
    providers.clear();
    for (Servlet servlet : servlets) {
      if (servlet instanceof CamelServlet) {
        providers.add((CamelServlet) servlet);
      }
    }
  }

  @Override
  public CamelServlet getCamelServlet(String servletName) {

    for (CamelServlet servlet : providers) {
      if (servlet.getServletName().equals(servletName)) {
        return servlet;
      }
    }
    return null;
  }
}
