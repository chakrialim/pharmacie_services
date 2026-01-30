package pharmacie.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.validation.ConstraintViolationException;
import pharmacie.dao.DispensaireRepository;
import pharmacie.dao.MedicamentRepository;
import pharmacie.dao.LigneRepository;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
// Ce test est basé sur le jeu de données dans "test_data.sql"
class CreationCommandeTest {
    private static final String ID_PETIT_CLIENT = "0COM";
    private static final String ID_GROS_CLIENT = "2COM";
    private static final BigDecimal REMISE_POUR_GROS_CLIENT = new BigDecimal("0.15");
    
    // IDs pour les tests de ajouterLigne
    private static final int COMMANDE_EN_COURS = 99998;
    private static final int COMMANDE_ENVOYEE = 99999;
    private static final int MEDICAMENT_DISPONIBLE = 93;
    private static final int MEDICAMENT_INDISPONIBLE = 97;
    private static final int MEDICAMENT_INEXISTANT = 99999;

    @Autowired
    private CommandeService service;
    @Autowired
    private DispensaireRepository daoClient;
    @Autowired
    private MedicamentRepository medicamentDao;
    @Autowired
    private LigneRepository ligneDao;

    @Test
    void testCreerCommandePourGrosClient() {
        var commande = service.creerCommande(ID_GROS_CLIENT);
        assertNotNull(commande.getNumero(), "On doit avoir la clé de la commande");
        assertEquals(REMISE_POUR_GROS_CLIENT, commande.getRemise(),
            "Une remise de 15% doit être appliquée pour les gros clients");
    }

    @Test
    void testCreerCommandePourPetitClient() {
        var commande = service.creerCommande(ID_PETIT_CLIENT);
        assertNotNull(commande.getNumero());
        assertEquals(BigDecimal.ZERO, commande.getRemise(),
            "Aucune remise ne doit être appliquée pour les petits clients");
    }

    @Test
    void testCreerCommandeInitialiseAdresseLivraison() {
        var commande = service.creerCommande(ID_PETIT_CLIENT);
        var client = daoClient.findById(ID_PETIT_CLIENT).orElseThrow();
        assertEquals(client.getAdresse(), commande.getAdresseLivraison(),
            "On doit recopier l'adresse du client dans l'adresse de livraison");
    }

    @Test
    void ajouterLigneCommande() {
        var ligne = service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE, 5);
        assertNotNull(ligne.getId(), "La ligne doit être enregistrée et avoir une clé");
        assertEquals(5, ligne.getQuantite(), "La quantité commandée doit être celle demandée");
    }

    @Test
    void ajouterLigneCommande_MedicamentIndisponible() {
        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_INDISPONIBLE, 5);
        }, "On ne peut pas ajouter une ligne pour un médicament indisponible");
    }

    @Test
    void ajouterLigneCommande_MedicamentInexistant() {
        assertThrows(NoSuchElementException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_INEXISTANT, 5);
        }, "On ne peut pas ajouter une ligne pour un médicament inexistant");
    }

    @Test
    void ajouterLigneCommande_CommandeEnvoyee() {
        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(COMMANDE_ENVOYEE, MEDICAMENT_DISPONIBLE, 5);
        }, "On ne peut pas ajouter une ligne à une commande déjà envoyée");
    }

    @Test
    void ajouterLigneCommande_QuantiteNegative() {
        assertThrows(ConstraintViolationException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE, -5);
        }, "La quantité commandée doit être positive");
    }

    @Test 
    void ajouterLigneCommande_QuantiteSuperieureAuStock() {
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        int quantiteEnStock = medicament.getUnitesEnStock();
        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE, quantiteEnStock + 1);
        }, "On ne peut pas commander plus que le stock disponible");
    }

    @Test
    void supprimerLigneCommande() {
        var ligne = service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE, 3);
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        int stockReserveAvantSuppression = medicament.getUnitesCommandees();

        service.supprimerLigne(ligne.getId());

        var medicamentApres = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        int stockReserveApresSuppression = medicamentApres.getUnitesCommandees();
        assertEquals(stockReserveAvantSuppression - 3, stockReserveApresSuppression,
            "La suppression de la ligne doit décrémenter le stock réservé du médicament");
    }

    @Test
    void supprimerLigneCommande_CommandeNonTrouvee() {
        assertThrows(NoSuchElementException.class, () -> {
            service.supprimerLigne(999999);
        }, "On ne peut pas supprimer une ligne inexistante");
    }

    @Test
    void enregistreExpedition() {
        var commande = service.enregistreExpedition(COMMANDE_EN_COURS);
        assertNotNull(commande.getEnvoyeele(),
            "La date d'expédition doit être enregistrée lors de l'expédition"); 
    }

    @Test
    void enregistreExpedition_CommandeDejaEnvoyee() {
        assertThrows(IllegalStateException.class, () -> {
            service.enregistreExpedition(COMMANDE_ENVOYEE);
        }, "On ne peut pas expédier une commande déjà envoyée");
    }

    @Test
    void enregistreExpedition_StockMisAJour() {
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        int stockAvantExpedition = medicament.getUnitesEnStock();

        service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE, 4);
        service.enregistreExpedition(COMMANDE_EN_COURS);

        var medicamentApres = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        int stockApresExpedition = medicamentApres.getUnitesEnStock();

        assertEquals(stockAvantExpedition - 4, stockApresExpedition,
            "Le stock réel doit être décrémenté lors de l'expédition");
    }

    
}
