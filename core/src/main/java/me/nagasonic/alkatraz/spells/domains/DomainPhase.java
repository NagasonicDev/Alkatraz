package me.nagasonic.alkatraz.spells.domains;

/** Lifecycle of a domain. Recede walks the claim list backwards. */
public enum DomainPhase {
    EXPANDING, HOLDING, RECEDING, END
}
