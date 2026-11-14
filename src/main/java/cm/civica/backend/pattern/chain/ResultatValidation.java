package cm.civica.backend.pattern.chain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultatValidation {

    private boolean valide;
    private String motifRejet;
    @Builder.Default
    private List<String> avertissements = new ArrayList<>();

    public static ResultatValidation succes() {
        return ResultatValidation.builder()
                .valide(true)
                .avertissements(new ArrayList<>())
                .build();
    }

    public static ResultatValidation succesAvecAvertissement(String avertissement) {
        List<String> avs = new ArrayList<>();
        avs.add(avertissement);
        return ResultatValidation.builder()
                .valide(true)
                .avertissements(avs)
                .build();
    }

    public static ResultatValidation echec(String motifRejet) {
        return ResultatValidation.builder()
                .valide(false)
                .motifRejet(motifRejet)
                .avertissements(new ArrayList<>())
                .build();
    }
}

