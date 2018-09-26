package com.isaacsheff.charlotte.experiments;

import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import io.grpc.stub.StreamObserver;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 
 * Used by AgreementNClient to wait for responses from the Agreement servers. 
 * Literally just calls onFernResponse whenever a response comes in.
 */
public class AgreementNObserver implements StreamObserver<RequestIntegrityAttestationResponse> {
  /** Use logger for logging events on this class. */
  private static final Logger logger = Logger.getLogger(AgreementNObserver.class.getName());

  /** The AgreementNClient for which this observer exists. */
  private final AgreementNClient agreementClient;

  /** The request to the Fern server we're waiting for a response from **/
  private final RequestIntegrityAttestationInput request;

  /**
   * Constructor.
   * @param agreementClient the AgreementNClient for which this exists
   * @param request The request to the Fern server we're waiting for a response from 
   */
  public AgreementNObserver(AgreementNClient agreementClient, RequestIntegrityAttestationInput request) {
    this.agreementClient = agreementClient;
    this.request = request;
  }

  /** @return The AgreementNClient for which this observer exists. */
  public AgreementNClient getAgreementClient() {return agreementClient;}

  /** @return The request to the Fern server we're waiting for a response from **/
  public RequestIntegrityAttestationInput getRequest() {return request;}

  /**
   * What do we do each time a response arrives back from the wire.
   * Call getAgreementClient().onFernResponse.
   * @param input the new RequestIntegrityAttestationResponse that has just arrived on the wire.
   */
  public void onNext(RequestIntegrityAttestationResponse response) {
    getAgreementClient().onFernResponse(response, getRequest());
  }

  /**
   * What do we do when the RPC is over (input stream closes).
   * We are done. do nothing.
   */
  public void onCompleted() {}

  /**
   * Called when there is an error in the stream.
   * We log it as a warning.
   * @param t the error arising from the stream.
   */
  public void onError(Throwable t) {
    logger.log(Level.WARNING, "requestIntegrityAttestation cancelled", t);
  }
}
