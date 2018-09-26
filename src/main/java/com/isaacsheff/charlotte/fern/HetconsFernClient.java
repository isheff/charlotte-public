package com.isaacsheff.charlotte.fern;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.yaml.Contact;

public class HetconsFernClient  extends  AgreementFernClient {

    public HetconsFernClient(CharlotteNodeService localService, Contact contact) {
        super(localService, contact);
    }
}
