package fr.tropimon.casino;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CasinoBackendContractTest {
    private static String migration() throws Exception {
        return Files.readString(Path.of("supabase", "tropimon_casino.sql"));
    }

    @Test void backendOwnsRandomDrawBalanceAndIdempotency() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("extensions.gen_random_bytes(4)"));
        assertTrue(sql.contains("rejection_limit:=4294967296-(4294967296%total)"));
        assertTrue(sql.contains("exit when value<rejection_limit"));
        assertTrue(sql.contains("as cumulative_weight"));
        assertTrue(sql.contains("roll<weighted.cumulative_weight"));
        assertTrue(sql.contains("unique(user_id, request_id)"));
        assertTrue(sql.contains("for update"));
        assertTrue(sql.contains("chips=chips-p_bet+won"));
        assertTrue(sql.contains("profile.role<>'super_admin' and profile.chips<p_bet"));
        assertTrue(sql.contains("new_balance:=profile.chips"));
    }

    @Test void backendProtectsRolesPaymentsAndSeventyTwoHourQueue() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("SELF_REVIEW_FORBIDDEN"));
        assertTrue(sql.contains("SELF_PAYMENT_FORBIDDEN"));
        assertTrue(sql.contains("SUPER_ADMIN_REQUIRED"));
        assertTrue(sql.contains("BANK_CANNOT_TRANSACT"));
        assertTrue(sql.contains("interval '72 hours'"));
        assertTrue(sql.contains("enable row level security"));
        assertTrue(sql.contains("revoke execute on all functions in schema tropimon_casino from public,anon"));
        assertTrue(sql.contains("status='pending' or (kind='withdrawal' and status='approved')"));
    }

    @Test void depositsBetsAndWithdrawalsUsePokeDollarsDirectly() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("poke_dollars_per_chip integer not null default 1"));
        assertTrue(sql.contains("chip_amount:=p_poke_dollars"));
        assertTrue(sql.contains("'withdrawal',p_chips,p_chips"));
        assertTrue(sql.contains("poke_dollars_per_chip=1"));
    }

    @Test void sevenSymbolsKeepExpectedWeightAndHouseEdgeContract() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("('oran',1,30,1,2)"));
        assertTrue(sql.contains("('tropimon',7,2,15,150)"));
        assertEquals(0.931046, SlotOdds.theoreticalReturn(), 0.000001);
    }

    @Test void deploymentNeverContainsOrReopensAReusableBootstrapHash() throws Exception {
        String sql = migration();
        assertFalse(sql.contains("decode('"));
        assertTrue(sql.contains("on conflict(singleton) do nothing"));
    }
}
