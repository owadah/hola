/**
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.developers.msa.hola;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.apache.deltaspike.core.api.config.ConfigResolver;
import org.jboss.jbossts.star.util.TxStatus;
import org.jboss.jbossts.star.util.TxSupport;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;

import io.swagger.annotations.ApiOperation;

@Path("/")
public class HolaResource {

    private static final String txCoordinator = "http://wildfly-rts:8080";
    private static final String txCoordinatorUrl = txCoordinator + "/rest-at-coordinator/tx/transaction-manager";


    @Inject
    private AlohaService alohaService;

    @Context
    private SecurityContext securityContext;

    @Context
    private HttpServletRequest servletRequest;

    @GET
    @Path("/hola")
    @Produces("text/plain")
    @ApiOperation("Returns the greeting in Spanish")
    public String hola() {
        String hostname = System.getenv().getOrDefault("HOSTNAME", "unknown");
        String translation = ConfigResolver
            .resolve("hello")
            .withDefault("Hola de %s")
            .logChanges(true)
            // 5 Seconds cache only for demo purpose
            .cacheFor(TimeUnit.SECONDS, 5)
            .getValue();
        return String.format(translation, hostname);

    }

    @GET
    @Path("/hola-chaining")
    @Produces("application/json")
    @ApiOperation("Returns the greeting plus the next service in the chain")
    public List<String> holaChaining() {
        TxSupport txSupport = new TxSupport(txCoordinatorUrl);

        txSupport.startTx();

        String participantUid = Integer.toString(new Random().nextInt(Integer.MAX_VALUE) + 1);
        String header = txSupport.makeTwoPhaseUnAwareParticipantLinkHeader("http://hola:8080/api", /*volatile*/ false, participantUid, null, true);
        System.out.println("Header :" + header);
        String enlistmentUri = txSupport.getDurableParticipantEnlistmentURI();
        System.out.println("Enlistment url: " + enlistmentUri);
        String participant = new TxSupport().enlistParticipant(enlistmentUri, header);
        System.out.println("Enlisted participant url: " + participant);

        List<String> greetings = new ArrayList<>();
        greetings.add(hola());
        greetings.addAll(alohaService.aloha(enlistmentUri));

        String committed = txSupport.commitTx();
        System.out.println("committed string: " + committed);

        return greetings;
    }

    @GET
    @Path("/hola-secured")
    @Produces("text/plain")
    @ApiOperation("Returns a message that is only available for authenticated users")
    public String holaSecured() {
        // this will set the user id as userName
        String userName = securityContext.getUserPrincipal().getName();

        if (securityContext.getUserPrincipal() instanceof KeycloakPrincipal) {
            @SuppressWarnings("unchecked")
            KeycloakPrincipal<KeycloakSecurityContext> kp = (KeycloakPrincipal<KeycloakSecurityContext>) securityContext.getUserPrincipal();

            // this is how to get the real userName (or rather the login name)
            userName = kp.getKeycloakSecurityContext().getToken().getName();
        }
        return "This is a Secured resource. You are logged as " + userName;

    }

    @GET
    @Path("/logout")
    @Produces("text/plain")
    @ApiOperation("Logout")
    public String logout() throws ServletException {
        servletRequest.logout();
        return "Logged out";
    }

    @GET
    @Path("/health")
    @Produces("text/plain")
    @ApiOperation("Used to verify the health of the service")
    public String health() {
        return "I'm ok";
    }


    // -------- TXN handling
    @PUT
    @Path("/{pUid}/prepare")
    public Response prepare(@PathParam("pUid") String pUid, String content) {
        System.out.println("Prepare called with pUid: " + pUid + ", content: " + content);
        return Response.ok(TxSupport.toStatusContent(TxStatus.TransactionPrepared.name())).build();
    }

    @PUT
    @Path("/{pUid}/commit")
    public Response commit(@PathParam("pUid") String pUid, String content) {
        System.out.println("Commit called with pid: " + pUid + ", content: " + content);
        return Response.ok(TxSupport.toStatusContent(TxStatus.TransactionCommitted.name())).build();
    }

    @PUT
    @Path("/{pUid}/rollback")
    public Response rollback(@PathParam("pUid") String pUid, String content) {
        System.out.println("Rollback called with pid: " + pUid + ", content: " + content);
        return Response.ok(TxSupport.toStatusContent(TxStatus.TransactionRolledBack.name())).build();
    }

    @PUT
    @Path("/{pUid}/commit-one-phase")
    public Response commmitOnePhase(@PathParam("pUid") String pUid, String content) {
        System.out.println("One phase commit called with pid: " + pUid + ", content: " + content);
        return Response.ok(TxSupport.toStatusContent(TxStatus.TransactionCommittedOnePhase.name())).build();
    }

    @HEAD
    @Path("/{pUid}/participant")
    public Response getTerminator(@Context UriInfo info, @PathParam("pUid") String pUid) {
        System.out.println("System participant getTerminator called with pid: " + pUid);
        Response.ResponseBuilder builder = Response.ok();
        builder.header("Link", new TxSupport().makeTwoPhaseUnAwareParticipantLinkHeader(txCoordinator + "/api", /*volatile*/ false, null, null, false));
        return builder.build();
    }

    @GET
    @Path("/{pUid}/participant")
    public String getStatus(@PathParam("pUid") String pUid) {
        System.out.println("System get status called with pid: " + pUid);
        return TxSupport.toStatusContent(TxStatus.TransactionActive.name());

    }

    @SuppressWarnings({"UnusedDeclaration"})
    @DELETE
    @Path("/{pUid}/participant")
    public void forgetWork(@PathParam("pUid") String pUid) {
        System.out.println("Delete with pUid: " + pUid);
    }

    // --- Debugging purposes
    private void sendGet(String url) {
        // sendGet("http://aloha:8080/api/aloha");
        // sendGet(txCoordinator);
        // sendGet(txCoordinatorUrl);
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            //add request header
            con.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = con.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            //print result
            System.out.println(response.toString());
        } catch (Exception e) {
            System.err.println("Error ouch! " + e.getMessage());
            e.printStackTrace();
        }
    }
}
