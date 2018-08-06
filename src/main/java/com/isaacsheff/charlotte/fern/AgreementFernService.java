package com.isaacsheff.charlotte.fern;

import com.isaacsheff.charlotte.proto.FernGrpc.FernImplBase;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;

import io.grpc.stub.StreamObserver;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AgreementFernService extends FernImplBase {

  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(AgreementFernService.class.getName());

  /**
   * TODO: DO THIS RIGHT!
   * @param request the request from the client sent over the wire
   * @param responseObserver used for sending a RequestIntegrityAttestationResponse back to the client over the wire
   */
  @Override
  public void requestIntegrityAttestation(final RequestIntegrityAttestationInput request,
                                          final StreamObserver<RequestIntegrityAttestationResponse> responseObserver) {
  }

}
