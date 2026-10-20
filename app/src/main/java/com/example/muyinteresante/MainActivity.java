package com.example.muyinteresante;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import android.os.Parcelable;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.muyinteresante.util.ConnectivityAndInternetAccess;
import com.example.muyinteresante.util.NewsCacheManager;
import com.example.muyinteresante.util.RemoteOperationPolicy;
import com.example.muyinteresanteNoTocar.DescargaNoticiasRSS;
import com.example.muyinteresanteNoTocar.NoticiaRSS;
import com.example.muyinteresanteNoTocar.iNoticiaRSS;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements iNoticiaRSS {

    private static final String TAG = "MainActivity";
    private static final String RSS_URL = "https://www.elplural.com/uploads/feeds/feed_elplural_es.xml";
    private static final String RSS_PAGE_URL = "https://www.elplural.com/uploads/feeds/feed_elplural_es.xml?page=";
    private static final int LOAD_MORE_THRESHOLD = 4;
    private static final int MAX_CONSECUTIVE_DUPLICATE_PAGES = 2;
    private static final String KEY_RECYCLER_VIEW_STATE = "recycler_view_state";
    private static final String KEY_NEXT_ARCHIVE_PAGE = "next_archive_page";
    private static final String KEY_HAS_MORE_NEWS = "has_more_news";
    private static final String KEY_CONSECUTIVE_DUPLICATE_PAGES = "consecutive_duplicate_pages";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvNoticias;
    private NoticiasAdapter adapter;
    private LinearLayoutManager layoutManager;

    private LinearLayout bannerNetworkNotice;
    private TextView tvBannerText;
    private Button btnDiagnosticarRed;

    private LinearLayout layoutNetworkStatusPill;
    private View viewNetworkDot;
    private TextView tvNetworkStatusText;

    private LinearLayout layoutEmptyState;
    private Button btnReintentar;

    private ConnectivityAndInternetAccess.NetworkObserver networkObserver;
    private ConnectivityAndInternetAccess.NetworkState currentNetworkState;

    private NewsViewModel newsState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        newsState = new ViewModelProvider(this).get(NewsViewModel.class);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        rvNoticias = findViewById(R.id.rvNoticias);
        bannerNetworkNotice = findViewById(R.id.bannerNetworkNotice);
        tvBannerText = findViewById(R.id.tvBannerText);
        btnDiagnosticarRed = findViewById(R.id.btnDiagnosticarRed);

        layoutNetworkStatusPill = findViewById(R.id.layoutNetworkStatusPill);
        viewNetworkDot = findViewById(R.id.viewNetworkDot);
        tvNetworkStatusText = findViewById(R.id.tvNetworkStatusText);

        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        btnReintentar = findViewById(R.id.btnReintentar);

        // Soporte para márgenes de ventana/cámara en smartphones tipo S25 Ultra
        final View topHeader = findViewById(R.id.topHeader);
        if (topHeader != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topHeader, new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                    int top = insets.getSystemWindowInsetTop();
                    int bottom = insets.getSystemWindowInsetBottom();
                    int left = insets.getSystemWindowInsetLeft();
                    int right = insets.getSystemWindowInsetRight();

                    if (topHeader != null) {
                        topHeader.setPadding(left, top, right, 0);
                    }
                    if (rvNoticias != null && bottom > 0) {
                        rvNoticias.setPadding(left, rvNoticias.getPaddingTop(), right, bottom + 12);
                    }
                    return insets;
                }
            });
        }

        layoutManager = new LinearLayoutManager(this);
        rvNoticias.setLayoutManager(layoutManager);
        adapter = new NoticiasAdapter(this, new ArrayList<NoticiaRSS>(), new NoticiasAdapter.OnNoticiaClickListener() {
            @Override
            public void onNoticiaClick(NoticiaRSS noticia) {
                if (noticia != null && noticia.getEnlace() != null) {
                    Intent intent = new Intent(MainActivity.this, DetalleActivity.class);
                    intent.putExtra(DetalleActivity.EXTRA_URL, noticia.getEnlace());
                    intent.putExtra(DetalleActivity.EXTRA_TITULO, noticia.getTitulo());
                    startActivity(intent);
                }
            }
        });
        rvNoticias.setAdapter(adapter);
        if (!newsState.visibleNews.isEmpty()) {
            adapter.updateData(newsState.visibleNews);
            rvNoticias.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }

        // Infinite scroll: cuando el usuario se aproxima al final se solicita la
        // siguiente página del feed oficial, que contiene noticias más antiguas.
        rvNoticias.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || newsState.isLoadingMore || !newsState.hasMoreNews || adapter == null) {
                    return;
                }

                int totalItems = adapter.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (totalItems > 0 && lastVisibleItem >= totalItems - 1 - LOAD_MORE_THRESHOLD) {
                    cargarMasNoticias();
                }
            }
        });

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                ejecutarDescargarNoticias();
            }
        });

        btnReintentar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ejecutarDescargarNoticias();
            }
        });

        View.OnClickListener listenerDiagnostico = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ejecutarDiagnosticoRedCompleto();
            }
        };
        layoutNetworkStatusPill.setOnClickListener(listenerDiagnostico);
        btnDiagnosticarRed.setOnClickListener(listenerDiagnostico);

        // En una recreación por rotación la Activity ya tiene estado y la lista
        // visible: restauramos caché/scroll, pero no repetimos la descarga RSS.
        if (savedInstanceState == null) {
            cargarNoticiasIniciales();
        } else {
            restaurarNoticiasTrasRecreacion(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (rvNoticias != null && rvNoticias.getLayoutManager() != null) {
            outState.putParcelable(
                    KEY_RECYCLER_VIEW_STATE,
                    rvNoticias.getLayoutManager().onSaveInstanceState());
        }
        outState.putInt(KEY_NEXT_ARCHIVE_PAGE, newsState.nextArchivePage);
        outState.putBoolean(KEY_HAS_MORE_NEWS, newsState.hasMoreNews);
        outState.putInt(KEY_CONSECUTIVE_DUPLICATE_PAGES, newsState.consecutiveDuplicatePages);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Iniciar el observador pasivo de conectividad basado en el Gist
        networkObserver = ConnectivityAndInternetAccess.observeNetwork(this, new ConnectivityAndInternetAccess.NetworkStateCallback() {
            @Override
            public void onStateChanged(ConnectivityAndInternetAccess.NetworkState state) {
                currentNetworkState = state;
                actualizarInterfazEstadoRed(state);
            }
        });
    }

    @Override
    protected void onStop() {
        if (adapter != null) {
            newsState.visibleNews.clear();
            newsState.visibleNews.addAll(adapter.getAllData());
        }
        super.onStop();
        if (networkObserver != null) {
            networkObserver.close();
            networkObserver = null;
        }
    }

    private void actualizarInterfazEstadoRed(ConnectivityAndInternetAccess.NetworkState state) {
        // NetworkState is the passive source of truth for the indicator. Do not use
        // isConnectedOrConnecting here: a stale connection attempt must not paint
        // the app green when there is no usable network.
        boolean hasNetwork = state != null
                ? state.isConnected()
                : ConnectivityAndInternetAccess.isConnected(this);
        boolean hasPhysicalNetwork = ConnectivityAndInternetAccess.hasPhysicalNetwork(this);
        boolean canStartRemoteRequest = hasNetwork && hasPhysicalNetwork;
        boolean isWifi = ConnectivityAndInternetAccess.isConnectedWifi(this);
        boolean isMobile = ConnectivityAndInternetAccess.isConnectedMobile(this);
        boolean isVpn = ConnectivityAndInternetAccess.vpnActive(this);
        boolean isAirplane = ConnectivityAndInternetAccess.isAirplaneModeOn(this);
        boolean isFast = ConnectivityAndInternetAccess.isConnectedFast(this);
        boolean isCaptive = state != null
                ? state.isCaptivePortalDetected()
                : ConnectivityAndInternetAccess.isCaptivePortalDetected(this);
        boolean isValidated = state != null
                ? state.isInternetValidated()
                : ConnectivityAndInternetAccess.isInternetValidated(this);
        boolean isStalled = ConnectivityAndInternetAccess.isConnectionAttemptStalled(this);

        Log.d(TAG, getString(R.string.main_text_01) + state +
                getString(R.string.main_text_02) + hasNetwork + getString(R.string.main_text_03) + hasPhysicalNetwork +
                getString(R.string.main_text_04) + canStartRemoteRequest +
                getString(R.string.main_text_05) + isWifi + getString(R.string.main_text_06) + isMobile +
                getString(R.string.main_text_07) + isVpn + getString(R.string.main_text_08) + isAirplane + getString(R.string.main_text_09) + isFast +
                getString(R.string.main_text_10) + isStalled);

        if (!canStartRemoteRequest) {
            // Disconnected / Offline
            layoutNetworkStatusPill.setBackgroundResource(R.drawable.bg_status_offline_badge);
            viewNetworkDot.setBackgroundResource(R.color.status_offline);
            tvNetworkStatusText.setText(isAirplane ? getString(R.string.main_text_11) : getString(R.string.main_text_12));
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_offline));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_offline_bg);
            tvBannerText.setText(isAirplane ?
                    getString(R.string.main_text_13) :
                    getString(R.string.main_text_14));
        } else if (isCaptive || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isValidated)) {
            // Captive Portal
            layoutNetworkStatusPill.setBackgroundResource(R.drawable.bg_status_warning_badge);
            viewNetworkDot.setBackgroundResource(R.color.status_warning);
            tvNetworkStatusText.setText(isCaptive ? getString(R.string.main_text_15) : getString(R.string.main_text_16));
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_warning));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_warning_bg);
            tvBannerText.setText(isCaptive
                    ? getString(R.string.main_text_17)
                    : getString(R.string.main_text_18));
        } else {
            // Fully connected & validated
            layoutNetworkStatusPill.setBackgroundResource(R.drawable.bg_status_badge);
            viewNetworkDot.setBackgroundResource(R.color.status_online);

            String statusType = getString(R.string.main_text_19);
            if (isVpn) {
                statusType = getString(R.string.main_text_20);
            } else if (isWifi) {
                statusType = getString(R.string.main_text_21);
            } else if (isMobile) {
                statusType = isFast ? getString(R.string.main_text_22) : getString(R.string.main_text_23);
            }
            tvNetworkStatusText.setText(statusType);
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_online));

            bannerNetworkNotice.setVisibility(View.GONE);
        }
    }

    private void cargarNoticiasIniciales() {
        NewsCacheManager.loadNewsFromCache(this, cached -> {
            if (isFinishing() || isDestroyed() || newsState.hasFreshFeed) return;
            if (cached != null && !cached.isEmpty() && adapter.getAllData().isEmpty()) {
                adapter.updateData(cached);
                layoutEmptyState.setVisibility(View.GONE);
                rvNoticias.setVisibility(View.VISIBLE);
            }
        });
        ejecutarDescargarNoticias();
    }


    private void restaurarNoticiasTrasRecreacion(final Bundle savedInstanceState) {
        Runnable scroll = () -> {
            Parcelable state = savedInstanceState.getParcelable(KEY_RECYCLER_VIEW_STATE);
            if (state != null) {
                rvNoticias.post(() -> {
                    if (layoutManager != null) layoutManager.onRestoreInstanceState(state);
                });
            }
        };
        if (!newsState.visibleNews.isEmpty()) {
            adapter.updateData(newsState.visibleNews);
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);
            scroll.run();
            return;
        }
        NewsCacheManager.loadNewsFromCache(this, cached -> {
            if (isFinishing() || isDestroyed() || newsState.hasFreshFeed) return;
            if (cached != null && !cached.isEmpty()) {
                adapter.updateData(cached);
                layoutEmptyState.setVisibility(View.GONE);
                rvNoticias.setVisibility(View.VISIBLE);
            } else {
                layoutEmptyState.setVisibility(View.VISIBLE);
                rvNoticias.setVisibility(View.GONE);
            }
            scroll.run();
        });
    }


    private void ejecutarDescargarNoticias() {
        // Guard barato: la petición RSS real es la prueba definitiva del feed.
        if (!RemoteOperationPolicy.canStartRequest(
                canStartRemoteRequest())) {
            // A refresh gesture or a previous callback may have left the spinner
            // active; offline must always settle it before showing cached content.
            swipeRefreshLayout.setRefreshing(false);
            Log.d(TAG, "Carga RSS omitida: no hay una red utilizable.");
            usarNoticiasOffline();
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        new DescargaNoticiasRSS(
                MainActivity.this,
                new DescargaNoticiasRSS.RemoteResultListener() {
            @Override
            public void onRemoteResult(DescargaNoticiasRSS.RemoteResult result) {
                manejarResultadoCargaInicial(result);
            }
        }, true).execute(RSS_URL, NoticiaRSS.RSS_MUY_INTERESANTE);
    }

    private boolean canStartRemoteRequest() {
        return RemoteOperationPolicy.canStartRequest(
                ConnectivityAndInternetAccess.isConnected(this),
                ConnectivityAndInternetAccess.hasPhysicalNetwork(this));
    }

    private void manejarResultadoCargaInicial(DescargaNoticiasRSS.RemoteResult result) {
        swipeRefreshLayout.setRefreshing(false);
        ArrayList<NoticiaRSS> noticias = result != null ? result.getNoticias() : null;
        if (noticias != null && !noticias.isEmpty()) {
            aplicarNoticiasIniciales(noticias);
            return;
        }

        diagnosticarFalloRSS("la carga de noticias", result, new Runnable() {
            @Override
            public void run() {
                usarNoticiasOffline();
            }
        });
    }

    private void aplicarNoticiasIniciales(ArrayList<NoticiaRSS> listaNoticias) {
        newsState.hasFreshFeed = true;
        adapter.updateData(listaNoticias);
        NewsCacheManager.saveNewsToCache(this, listaNoticias);
        layoutEmptyState.setVisibility(View.GONE);
        rvNoticias.setVisibility(View.VISIBLE);
        newsState.nextArchivePage = 2;
        newsState.hasMoreNews = true;
        newsState.isLoadingMore = false;
        newsState.consecutiveDuplicatePages = 0;
        Log.d(TAG, "Noticias recibidas con éxito: " + listaNoticias.size());
    }

    private void diagnosticarFalloRSS(
            final String operation,
            final DescargaNoticiasRSS.RemoteResult result,
            final Runnable afterClassification) {
        final Integer httpStatus = result != null ? result.getHttpStatus() : null;
        final Throwable failure = result != null ? result.getFailure() : null;

        // Un código HTTP ya demuestra comunicación con el servidor: no sondeamos
        // Internet de forma redundante después de una respuesta válida.
        if (!RemoteOperationPolicy.isAmbiguousConnectivityFailure(failure)
                || RemoteOperationPolicy.hasHttpResponse(httpStatus)) {
            Log.w(TAG, "Fallo de " + operation + " con respuesta/errores no ambiguos. HTTP=" + httpStatus, failure);
            Toast.makeText(this, getString(R.string.main_text_24), Toast.LENGTH_SHORT).show();
            if (afterClassification != null) {
                afterClassification.run();
            }
            return;
        }

        Log.w(TAG, "Fallo ambiguo de " + operation + "; ejecutando diagnóstico general posterior.", failure);
        ConnectivityAndInternetAccess.checkHttpOnlyAsyncDefault(
                this,
                new ConnectivityAndInternetAccess.InternetCallback() {
                    @Override
                    public void onResult(ConnectivityAndInternetAccess.InternetResult internetResult) {
                        boolean generalInternetWorks =
                                internetResult != null && internetResult.isReachable();
                        RemoteOperationPolicy.FailureDisposition disposition =
                                RemoteOperationPolicy.classifyFailure(
                                        httpStatus, failure, generalInternetWorks);

                        if (disposition == RemoteOperationPolicy.FailureDisposition.SERVICE_UNAVAILABLE) {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Internet funciona, pero el servicio de noticias no está disponible.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Problema de conectividad. Mostrando noticias guardadas.",
                                    Toast.LENGTH_LONG).show();
                        }
                        if (afterClassification != null) {
                            afterClassification.run();
                        }
                    }
                });
    }

    /**
     * Solicita una página adicional del feed oficial sin bloquear la interfaz con
     * un diálogo modal. Las páginas se acumulan en el adapter y se deduplican.
     */
    private void cargarMasNoticias() {
        if (newsState.isLoadingMore || !newsState.hasMoreNews || adapter == null) {
            return;
        }

        if (!RemoteOperationPolicy.canStartRequest(
                canStartRemoteRequest())) {
            swipeRefreshLayout.setRefreshing(false);
            Log.d(TAG, "No se cargan más noticias: sin conexión disponible.");
            Toast.makeText(this, getString(R.string.main_text_25), Toast.LENGTH_SHORT).show();
            return;
        }

        newsState.isLoadingMore = true;
        final int pageToLoad = newsState.nextArchivePage;
        Log.d(TAG, "Solicitando noticias antiguas. Página RSS: " + pageToLoad);

        new DescargaNoticiasRSS(
                MainActivity.this,
                new DescargaNoticiasRSS.RemoteResultListener() {
            @Override
            public void onRemoteResult(DescargaNoticiasRSS.RemoteResult result) {
                newsState.isLoadingMore = false;
                ArrayList<NoticiaRSS> listaNoticias =
                        result != null ? result.getNoticias() : null;
                if (listaNoticias == null) {
                    diagnosticarFalloRSS("la página " + pageToLoad, result, null);
                    return;
                }

                if (listaNoticias.isEmpty()) {
                    newsState.hasMoreNews = false;
                    Log.d(TAG, "Fin del archivo RSS alcanzado en la página " + pageToLoad);
                    Toast.makeText(MainActivity.this, getString(R.string.main_text_26), Toast.LENGTH_SHORT).show();
                    return;
                }

                int added = adapter.appendData(listaNoticias);
                newsState.nextArchivePage = pageToLoad + 1;

                if (added > 0) {
                    newsState.consecutiveDuplicatePages = 0;
                    NewsCacheManager.saveNewsToCache(MainActivity.this, adapter.getAllData());
                    Log.d(TAG, "Página " + pageToLoad + " cargada: " + added + " noticias nuevas (" + listaNoticias.size() + " recibidas).");
                } else {
                    newsState.consecutiveDuplicatePages++;
                    Log.d(TAG, "Página " + pageToLoad + " sin noticias nuevas tras deduplicar.");

                    if (newsState.consecutiveDuplicatePages >= MAX_CONSECUTIVE_DUPLICATE_PAGES) {
                        newsState.hasMoreNews = false;
                        Log.w(TAG, "Se detiene la paginación tras varias páginas consecutivas duplicadas.");
                    } else {
                        rvNoticias.post(new Runnable() {
                            @Override
                            public void run() {
                                cargarMasNoticias();
                            }
                        });
                    }
                }
            }
        }, false).execute(RSS_PAGE_URL + pageToLoad, NoticiaRSS.RSS_MUY_INTERESANTE);
    }

    private void usarNoticiasOffline() {
        if (adapter != null && !adapter.getAllData().isEmpty()) {
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);
            return;
        }
        NewsCacheManager.loadNewsFromCache(this, cached -> {
            if (isFinishing() || isDestroyed() || newsState.hasFreshFeed) return;
            if (cached != null && !cached.isEmpty()) {
                adapter.updateData(cached);
                layoutEmptyState.setVisibility(View.GONE);
                rvNoticias.setVisibility(View.VISIBLE);
            } else {
                rvNoticias.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
            }
        });
    }


    @Override
    public void onRecibeNoticiasRSS(ArrayList<NoticiaRSS> listaNoticias) {
        swipeRefreshLayout.setRefreshing(false);

        if (listaNoticias != null && !listaNoticias.isEmpty()) {
            adapter.updateData(listaNoticias);
            NewsCacheManager.saveNewsToCache(this, listaNoticias);
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);

            // Una actualización completa reinicia el recorrido del archivo.
            newsState.nextArchivePage = 2;
            // El feed inicial contiene la primera página; habilitamos la carga
            // progresiva de páginas antiguas al acercarse al final.
            newsState.hasMoreNews = true;
            newsState.isLoadingMore = false;
            newsState.consecutiveDuplicatePages = 0;

            Log.d(TAG, "Noticias recibidas con éxito: " + listaNoticias.size());
        } else {
            Toast.makeText(this, getString(R.string.main_text_27), Toast.LENGTH_SHORT).show();
            usarNoticiasOffline();
        }
    }

    private void ejecutarDiagnosticoRedCompleto() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_text_28))
                .setMessage(getString(R.string.main_text_29))
                .setPositiveButton(getString(R.string.main_text_30), null)
                .show();

        // Usa el estado observado más reciente, o una instantánea si todavía no
        // se ha iniciado el observador. Así el diagnóstico no pinta getString(R.string.main_text_31)
        // por una comprobación antigua o por un intento de conexión pendiente.
        ConnectivityAndInternetAccess.NetworkState diagnosticState = currentNetworkState != null
                ? currentNetworkState
                : ConnectivityAndInternetAccess.snapshotNetworkState(this);
        boolean hasNetwork = diagnosticState != null && diagnosticState.isConnected();
        boolean hasPhysicalNetwork = ConnectivityAndInternetAccess.hasPhysicalNetwork(this);
        boolean isWifi = ConnectivityAndInternetAccess.isConnectedWifi(this);
        boolean isMobile = ConnectivityAndInternetAccess.isConnectedMobile(this);
        boolean isFast = ConnectivityAndInternetAccess.isConnectedFast(this);
        boolean isVpn = ConnectivityAndInternetAccess.vpnActive(this);
        boolean isAirplane = ConnectivityAndInternetAccess.isAirplaneModeOn(this);

        // Sondeo activo DNS/HTTP
        ConnectivityAndInternetAccess.checkInternetAsyncDefault(this, new ConnectivityAndInternetAccess.InternetCallback() {
            @Override
            public void onResult(ConnectivityAndInternetAccess.InternetResult result) {
                if (dialog != null && dialog.isShowing()) {
                    boolean reachable = result != null && result.isReachable();
                    String reachedHost = result != null ? result.getReachedHost() : getString(R.string.main_text_32);
                    long time = result != null ? result.getElapsedMilliseconds() : 0;

                    StringBuilder sb = new StringBuilder();
                    sb.append(getString(R.string.main_text_33));
                    String networkSummary = !hasNetwork
                            ? getString(R.string.main_text_34)
                            : (!hasPhysicalNetwork
                            ? getString(R.string.main_text_35)
                            : getString(R.string.main_text_36));
                    sb.append(getString(R.string.main_text_37)).append(networkSummary).append("\n");
                    sb.append(getString(R.string.main_text_38)).append(isWifi ? getString(R.string.main_text_39) : (isMobile ? getString(R.string.main_text_40) : getString(R.string.main_text_41))).append("\n");
                    sb.append(getString(R.string.main_text_42)).append(isFast ? getString(R.string.main_text_43) : getString(R.string.main_text_44)).append("\n");
                    sb.append(getString(R.string.main_text_45)).append(isVpn ? getString(R.string.main_text_46) : getString(R.string.main_text_47)).append("\n");
                    sb.append(getString(R.string.main_text_48)).append(isAirplane ? getString(R.string.main_text_49) : getString(R.string.main_text_50)).append("\n\n");

                    sb.append(getString(R.string.main_text_51));
                    sb.append(getString(R.string.main_text_52)).append(reachable ? getString(R.string.main_text_53) : getString(R.string.main_text_54)).append("\n");
                    sb.append(getString(R.string.main_text_55)).append(reachedHost).append("\n");
                    sb.append(getString(R.string.main_text_56)).append(time).append(getString(R.string.main_text_57));

                    if (currentNetworkState != null) {
                        sb.append(getString(R.string.main_text_58));
                        sb.append(currentNetworkState.toString());
                    }

                    dialog.setMessage(sb.toString());
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem searchItem = menu.findItem(R.id.action_buscar);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.main_text_59));
                searchView.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override public boolean onMenuItemActionExpand(MenuItem item) {
                        searchView.post(new Runnable() {
                            @Override public void run() {
                                searchView.setIconified(false);
                                searchView.requestFocus();
                                android.view.inputmethod.InputMethodManager ime =
                                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                if (ime != null) {
                                    View focused = searchView.findFocus();
                                    ime.showSoftInput(focused != null ? focused : searchView,
                                            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                                }
                            }
                        });
                        return true;
                    }
                    @Override public boolean onMenuItemActionCollapse(MenuItem item) {
                        searchView.setQuery("", false);
                        if (adapter != null) adapter.filter("");
                        return true;
                    }
                });
                
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        if (adapter != null) {
                            adapter.filter(query);
                        }
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (adapter != null) {
                            adapter.filter(newText);
                        }
                        return true;
                    }
                });
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_actualizar) {
            ejecutarDescargarNoticias();
            return true;
        } else if (id == R.id.action_test_conectividad) {
            ejecutarDiagnosticoRedCompleto();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
