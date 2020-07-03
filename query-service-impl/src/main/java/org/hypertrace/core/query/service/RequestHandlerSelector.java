package org.hypertrace.core.query.service;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hypertrace.core.query.service.api.QueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestHandlerSelector {

  private static final Logger LOG = LoggerFactory.getLogger(RequestHandlerSelector.class);

  List<RequestHandler> requestHandlers = new ArrayList<>();

  public RequestHandlerSelector(List<RequestHandler> requestHandlers) {
    this.requestHandlers = requestHandlers;
  }

  public RequestHandlerSelector(RequestHandlerRegistry registry) {
    Collection<RequestHandlerInfo> requestHandlerInfoList = registry.getAll();
    for (RequestHandlerInfo requestHandlerInfo : requestHandlerInfoList) {
      try {
        Constructor<? extends RequestHandler> constructor =
            requestHandlerInfo.getRequestHandlerClazz().getConstructor(new Class[] {});
        RequestHandler requestHandler = constructor.newInstance();
        requestHandler.init(requestHandlerInfo.getName(), requestHandlerInfo.getConfig());
        requestHandlers.add(requestHandler);
      } catch (Exception e) {
        LOG.error("Error initializing request Handler:{}", requestHandlerInfo, e);
      }
    }
  }

  public RequestHandler select(QueryRequest request, RequestAnalyzer analyzer) {

    // check if each of the requestHandler can handle the request and return the cost of serving
    // that query
    double minCost = Double.MAX_VALUE;
    RequestHandler selectedHandler = null;
    Set<String> referencedColumns = analyzer.getReferencedColumns();
    Set<String> referencedSources = new HashSet<>(request.getSourceList());
    for (RequestHandler requestHandler : requestHandlers) {
      QueryCost queryCost = requestHandler.canHandle(request, referencedSources, referencedColumns);
      double cost = queryCost.getCost();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Request handler: {}, query cost: {}", requestHandler.getName(), cost);
      }
      if (cost >= 0 && cost < minCost) {
        minCost = cost;
        selectedHandler = requestHandler;
      }
    }

    if (selectedHandler != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(
            "Selected requestHandler: {} for the query: {}; referencedColumns: {}, cost: {}",
            selectedHandler.getName(),
            request,
            referencedColumns,
            minCost);
      }
    } else {
      LOG.error(
          "No requestHandler for the query: {}; referencedColumns: {}, cost: {}",
          request,
          referencedColumns,
          minCost);
    }
    return selectedHandler;
  }
}
