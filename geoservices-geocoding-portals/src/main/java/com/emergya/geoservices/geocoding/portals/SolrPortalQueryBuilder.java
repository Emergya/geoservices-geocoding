package com.emergya.geoservices.geocoding.portals;

import com.emergya.geoservices.geocoding.portals.antlr4.PortalsListener;
import com.emergya.geoservices.geocoding.portals.antlr4.PortalsParser;
import com.emergya.geoservices.geocoding.portals.antlr4.PortalsParser.RestAddressContext;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.solr.client.solrj.SolrQuery;

/**
 *
 * @author lroman
 */
public class SolrPortalQueryBuilder implements PortalsListener {

    private final SolrQuery result;
    
    private boolean portalSet;
    private boolean municipalitySet;

    public SolrPortalQueryBuilder() {
        result = new SolrQuery();
        result.setQuery("");
    }

    public SolrQuery getResultQuery(int maxResponses) {
        if(portalSet) {  
            result.setRows(maxResponses);
            result.set("sort", "score desc");
        } else {
            if(municipalitySet){
                result.setRows(1);
            }
            result.set("sort", "portal asc");
        }
        
        return result;
    }

    @Override
    public void enterAddress(PortalsParser.AddressContext ctx) {

    }

    @Override
    public void exitAddress(PortalsParser.AddressContext ctx) {

    }

    @Override
    public void enterPortal(PortalsParser.PortalContext ctx) {

    }

    @Override
    public void exitPortal(PortalsParser.PortalContext ctx) {
        if (ctx.isEmpty()) {
            return;
        }
        result.addFilterQuery("portal:" + ctx.getText());
    }
   

    @Override
    public void enterMunicipality(PortalsParser.MunicipalityContext ctx) {

    }

    @Override
    public void exitMunicipality(PortalsParser.MunicipalityContext ctx) {
        if (ctx.isEmpty()) {
            return;
        }
        this.municipalitySet = true;
        result.addFilterQuery(String.format("locality:\"%s\"", ctx.getText()));
    }

  
    @Override
    public void enterStreetname(PortalsParser.StreetnameContext ctx) {

    }

    @Override
    public void exitStreetname(PortalsParser.StreetnameContext ctx) {
        if (ctx.isEmpty()) {
            return;
        }
        
        
        
        // We add the street name at the end of the query.
        result.setQuery(ctx.getText());
    }

    @Override
    public void visitTerminal(TerminalNode tn) {

    }

    @Override
    public void visitErrorNode(ErrorNode en) {

    }

    @Override
    public void enterEveryRule(ParserRuleContext prc) {

    }

    @Override
    public void exitEveryRule(ParserRuleContext prc) {

    }

    @Override
    public void enterRestAddress(RestAddressContext ctx) {
        
    }

    @Override
    public void exitRestAddress(RestAddressContext ctx) {
        
    }

}
