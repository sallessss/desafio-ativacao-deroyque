package com.hugosalles.desafioativacao.repository;

import com.hugosalles.desafioativacao.entity.SolicitacaoAtivacao;
import com.hugosalles.desafioativacao.entity.StatusSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

public interface SolicitacaoAtivacaoRepository extends JpaRepository<SolicitacaoAtivacao, UUID>,
        JpaSpecificationExecutor<SolicitacaoAtivacao> {

    @Query("SELECT s FROM SolicitacaoAtivacao s WHERE s.id = :id AND s.identificadorConsumidor = :consumidor")
    Optional<SolicitacaoAtivacao> buscarPorIdEConsumidor(@Param("id") UUID id, @Param("consumidor") String identificadorConsumidor);

    @Query("SELECT s.status AS status, COUNT(s) AS quantidade FROM SolicitacaoAtivacao s GROUP BY s.status")
    List<ContagemPorStatus> contarAgrupadoPorStatus();

    interface ContagemPorStatus {
        StatusSolicitacao getStatus();

        long getQuantidade();
    }

    @Query(value = """
            SELECT * FROM solicitacoes_ativacao
            WHERE status = 'PENDING' AND next_attempt_at <= :agora
            ORDER BY next_attempt_at, created_at
            LIMIT :limite FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<SolicitacaoAtivacao> buscarPendentesParaReserva(@Param("agora") Instant agora, @Param("limite") int limite);

    @Query(value = """
            SELECT * FROM solicitacoes_ativacao
            WHERE status = 'PROCESSING' AND processing_started_at < :limite
            ORDER BY processing_started_at
            LIMIT :quantidade FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<SolicitacaoAtivacao> buscarProcessamentosPresos(@Param("limite") Instant limite, @Param("quantidade") int quantidade);
}
