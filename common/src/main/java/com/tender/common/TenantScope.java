package com.tender.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Call at the start of every @Transactional method: arms Postgres row-level security.
 */
@Component
public class TenantScope {
    @PersistenceContext
    private EntityManager em;

    public void apply() {
        em.createNativeQuery("select set_config('app.tenant_id', ?1, true)")
                .setParameter(1, TenantContext.require().toString()).getSingleResult();
    }
}
