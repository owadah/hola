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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.deltaspike.core.api.config.ConfigResolver;
import org.jboss.jbossts.star.util.TxStatus;
import org.jboss.jbossts.star.util.TxSupport;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;

import io.swagger.annotations.ApiOperation;

@Path("/")
public class HolaResource {
    private String txCoordinatorUrl = "http://localhost:8080/rest-at-coordinator/tx/transaction-manager";

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
        List<String> greetings = new ArrayList<>();
        greetings.add(hola());
        greetings.addAll(alohaService.aloha());
        return greetings;
    }

    @GET
    @Path("/hola-chaining-tx")
    @Produces("application/json")
    @ApiOperation("Returns the greeting plus the next service in the chain")
    public List<String> holaChainingTx() {
        TxSupport txSupport = new TxSupport(txCoordinatorUrl);

        txSupport.startTx();
        String header = txSupport.makeTwoPhaseUnAwareParticipantLinkHeader("http://localhost:8180", false, null, null, false);
        String participant = new TxSupport().enlistParticipant("http://localhost:8180", header);

        List<String> greetings = new ArrayList<>();
        greetings.add(hola());
        greetings.addAll(alohaService.aloha());

        txSupport.commitTx();
        return greetings;
    }

    @PUT
    @Path("{pId}/{tId}/prepare")
    public Response prepare(@PathParam("pId") @DefaultValue("")String pId, @PathParam("tId") @DefaultValue("")String tId, String content) {
        System.out.println("Prepare called with pid: " + pId + ", tId: " + tId + ", content: " + content);
        return Response.ok(TxSupport.toStatusContent(TxStatus.TransactionPrepared.name())).build();
    }

    @PUT
    @Path("{pId}/{tId}/commit")
    public Response commit(@PathParam("pId") @DefaultValue("")String pId, @PathParam("tId") @DefaultValue("")String tId, String content) {
        System.out.println("Commit called with pid: " + pId + ", tId: " + tId + ", content: " + content);
        return Response.ok(TxSupport.toStatusContent(TxStatus.TransactionCommitted.name())).build();
    }

    @PUT
    @Path("{pId}/{tId}/rollback")
    public Response rollback(@PathParam("pId") @DefaultValue("")String pId, @PathParam("tId") @DefaultValue("")String tId, String content) {
        System.out.println("Rollback called with pid: " + pId + ", tId: " + tId + ", content: " + content);
        return Response.ok(TxSupport.toStatusContent(TxStatus.TransactionRolledBack.name())).build();
    }

    @PUT
    @Path("{pId}/{tId}/commit-one-phase")
    public Response commmitOnePhase(@PathParam("pId") @DefaultValue("")String pId, @PathParam("tId") @DefaultValue("")String tId, String content) {
        System.out.println("One phase commit called with pid: " + pId + ", tId: " + tId + ", content: " + content);
        return Response.ok(TxSupport.toStatusContent(TxStatus.TransactionCommittedOnePhase.name())).build();
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
}
