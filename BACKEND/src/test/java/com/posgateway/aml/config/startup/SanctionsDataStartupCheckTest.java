package com.posgateway.aml.config.startup;

import com.posgateway.aml.client.aml.SanctionsCountClient;
import com.posgateway.aml.service.download.SanctionsListDownloadService;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The sanctions startup check must give the same outcome for the same configuration and state. */
class SanctionsDataStartupCheckTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private SanctionsDataStartupCheck check(SanctionsCountClient client) {
        return check(client, null);
    }

    @SuppressWarnings("unchecked")
    private SanctionsDataStartupCheck check(SanctionsCountClient client, SanctionsListDownloadService download) {
        ObjectProvider<SanctionsListDownloadService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(download);
        SanctionsDataStartupCheck c = new SanctionsDataStartupCheck(client, provider);
        c.clock = now::get;
        c.sleeper = now::addAndGet; // sleeping advances the fake clock
        return c;
    }

    private ApplicationReadyEvent event(String... profilesAndProps) {
        MockEnvironment env = new MockEnvironment();
        for (String s : profilesAndProps) {
            if (s.contains("=")) {
                String[] kv = s.split("=", 2);
                env.setProperty(kv[0], kv[1]);
            } else {
                env.addActiveProfile(s);
            }
        }
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        when(ctx.getEnvironment()).thenReturn(env);
        ApplicationReadyEvent ev = mock(ApplicationReadyEvent.class);
        when(ev.getApplicationContext()).thenReturn(ctx);
        return ev;
    }

    @Test
    void productionRetriesUntilMicroserviceAnswers() {
        SanctionsCountClient client = mock(SanctionsCountClient.class);
        when(client.getCount()).thenReturn(-1L, -1L, 6L);

        assertThatCode(() -> check(client).onApplicationEvent(event("production"))).doesNotThrowAnyException();
        verify(client, times(3)).getCount();
    }

    @Test
    void productionFailsClosedWhenCountStaysUnavailable() {
        SanctionsCountClient client = mock(SanctionsCountClient.class);
        when(client.getCount()).thenReturn(-1L);

        assertThatThrownBy(() -> check(client).onApplicationEvent(event("production")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNAVAILABLE");
        // 60 s window / 2 s interval -> 31 attempts, then stop.
        verify(client, times(31)).getCount();
    }

    @Test
    void productionFailsClosedOnEmptyWatchlist() {
        SanctionsCountClient client = mock(SanctionsCountClient.class);
        when(client.getCount()).thenReturn(0L);

        assertThatThrownBy(() -> check(client).onApplicationEvent(event("production")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void nonProductionWarnsOnceWithoutWaiting() {
        SanctionsCountClient client = mock(SanctionsCountClient.class);
        when(client.getCount()).thenReturn(-1L);

        assertThatCode(() -> check(client).onApplicationEvent(event("dev"))).doesNotThrowAnyException();
        verify(client, times(1)).getCount();
    }

    @Test
    void operatorCanOptOutOfFailClosedOnUnavailable() {
        SanctionsCountClient client = mock(SanctionsCountClient.class);
        when(client.getCount()).thenReturn(-1L);

        assertThatCode(() -> check(client).onApplicationEvent(event("production",
                "sanctions.startup.fail-closed-on-unavailable=false",
                "sanctions.startup.count-wait-seconds=4"))).doesNotThrowAnyException();
        verify(client, times(3)).getCount();
        assertThat(now.get()).isEqualTo(1_000_000L + 4_000L);
    }

    @Test
    void emptyWatchlistWithDownloadEnabledIsSeededOnFirstBoot() {
        SanctionsCountClient client = mock(SanctionsCountClient.class);
        when(client.getCount()).thenReturn(0L, 1200L);
        SanctionsListDownloadService download = mock(SanctionsListDownloadService.class);
        when(download.isDownloadEnabled()).thenReturn(true);

        assertThatCode(() -> check(client, download).onApplicationEvent(event("production")))
                .doesNotThrowAnyException();
        verify(download).manualDownload();
    }

    @Test
    void emptyWatchlistWithDownloadDisabledStillFailsClosed() {
        SanctionsCountClient client = mock(SanctionsCountClient.class);
        when(client.getCount()).thenReturn(0L);
        SanctionsListDownloadService download = mock(SanctionsListDownloadService.class);
        when(download.isDownloadEnabled()).thenReturn(false);

        assertThatThrownBy(() -> check(client, download).onApplicationEvent(event("production")))
                .isInstanceOf(IllegalStateException.class);
        verify(download, times(0)).manualDownload();
    }
}
