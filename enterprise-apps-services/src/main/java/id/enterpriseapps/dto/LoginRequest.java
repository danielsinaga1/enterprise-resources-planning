package id.enterpriseapps.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String npk;

    /**
     * Dikecualikan dari toString(): Spring mencatat objek request hasil deserialisasi pada level
     * DEBUG, sehingga tanpa ini password asli pengguna tertulis apa adanya ke journalctl
     * (terbukti terjadi di server dev). Jangan hapus anotasi ini.
     */
    @ToString.Exclude
    private String password;
}

