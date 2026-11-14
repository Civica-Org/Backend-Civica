package cm.civica.backend.security;

import cm.civica.backend.model.Utilisateur;
import cm.civica.backend.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        Utilisateur utilisateur = utilisateurRepository.findByIdentifier(identifier.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé avec l'identifiant (email ou téléphone) : " + identifier));

        if (!Boolean.TRUE.equals(utilisateur.getActif())) {
            throw new UsernameNotFoundException("Ce compte utilisateur est désactivé.");
        }

        return UserDetailsImpl.build(utilisateur);
    }
}

