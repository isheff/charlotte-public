package com.isaacsheff.charlotte.experiments;

import com.isaacsheff.charlotte.proto.RequestAvailabilityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestAvailabilityAttestationResponse;
import com.isaacsheff.charlotte.wilbur.WilburClient;

import io.grpc.stub.StreamObserver;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 
 * Used by AgreementNWClient to wait for responses from the Wilbur servers. 
 * Literally just calls onWilburResponse whenever a response comes in.
 * @author Isaac Sheff
 */
public class AgreementNWObserver implements StreamObserver<RequestAvailabilityAttestationResponse> {
  /** Use logger for logging events on this class. */
  private static final Logger logger = Logger.getLogger(AgreementNObserver.class.getName());

  /** The AgreementNWClient for which this observer exists. */
  private final AgreementNWClient agreementClient;

  /** The request to the Fern server we're waiting for a response from **/
  private final RequestAvailabilityAttestationInput request;

  /** the wilbur client we're using **/
  private final WilburClient wilburClient;

  /**
   * Constructor.
   * @param agreementClient the AgreementNClient for which this exists
   * @param request The request to the Fern server we're waiting for a response from 
   */
  public AgreementNWObserver(final AgreementNWClient agreementClient,
                             final WilburClient wilburClient,
                             final RequestAvailabilityAttestationInput request) {
    this.agreementClient = agreementClient;
    this.request = request;
    this.wilburClient = wilburClient;
  }

  /** @return The AgreementNClient for which this observer exists. */
  public AgreementNWClient getAgreementClient() {return agreementClient;}

  /** @return The request to the Fern server we're waiting for a response from **/
  public RequestAvailabilityAttestationInput getRequest() {return request;}

  /**
   * What do we do each time a response arrives back from the wire.
   * Call getAgreementClient().onFernResponse.
   * @param input the new RequestAvailabilityAttestationResponse that has just arrived on the wire.
   */
  public void onNext(final RequestAvailabilityAttestationResponse response) {
    getAgreementClient().onWilburResponse(getRequest(), response, wilburClient);
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
  public void onError(final Throwable t) {
    logger.log(Level.WARNING, "requestAvailabilityAttestation cancelled", t);
  }
}
