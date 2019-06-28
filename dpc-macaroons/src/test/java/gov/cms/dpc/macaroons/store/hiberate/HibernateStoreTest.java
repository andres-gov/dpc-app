package gov.cms.dpc.macaroons.store.hiberate;

import gov.cms.dpc.macaroons.store.AbstractStoreTest;
import gov.cms.dpc.macaroons.store.hibernate.HibernateKeyStore;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import java.security.SecureRandom;

class HibernateStoreTest extends AbstractStoreTest {

    HibernateStoreTest() {
        super(new HibernateKeyStore(buildSessionFactory(), new SecureRandom()));
    }

    static SessionFactory buildSessionFactory() {
        final Configuration conf = new Configuration();
        return conf.configure().buildSessionFactory();
    }

    @Override
    protected void teardown() {
        try (SessionFactory sessionFactory = buildSessionFactory()) {
            try (Session session = sessionFactory.openSession()) {
                final Transaction tx = session.beginTransaction();
                session.createSQLQuery("DROP TABLE root_keys CASCADE").executeUpdate();
                tx.commit();
            }
        }
    }
}
