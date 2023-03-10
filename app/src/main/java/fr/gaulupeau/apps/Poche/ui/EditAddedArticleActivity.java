package fr.gaulupeau.apps.Poche.ui;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.EventHelper;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.events.LocalArticleReplacedEvent;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;
import fr.gaulupeau.apps.Poche.service.workers.OperationsWorker;

public class EditAddedArticleActivity extends AppCompatActivity {

    public static final String PARAM_ARTICLE_URL = "article_url";

    private static final String TAG = EditAddedArticleActivity.class.getSimpleName();

    private static final String STATE_DISCOVERED_ARTICLE_ID = "discovered_article_id";
    private static final String STATE_ARCHIVED = "archived";
    private static final String STATE_FAVORITE = "favorite";
    private static final String STATE_OPEN_PENDING = "open_pending";
    private static final String STATE_INITIAL_OPEN_TIME = "initial_open_time";
    private static final String STATE_AUTOCLOSE_CANCELLED = "autoclose_cancelled";

    private static final long AUTOCLOSE_DELAY = TimeUnit.SECONDS.toMillis(7);

    private final EnumSet<FeedsChangedEvent.ChangeType> CHANGE_SET_FOR_REINIT = EnumSet.of(
            FeedsChangedEvent.ChangeType.TITLE_CHANGED,
            FeedsChangedEvent.ChangeType.FAVORITED,
            FeedsChangedEvent.ChangeType.UNFAVORITED,
            FeedsChangedEvent.ChangeType.ARCHIVED,
            FeedsChangedEvent.ChangeType.UNARCHIVED,
            FeedsChangedEvent.ChangeType.DELETED
    );

    private TextView articleTitleTv;
    private ImageButton favoriteButton;
    private ImageButton archiveButton;
    private ImageButton openButton;

    private long initialOpenTime;

    private Handler handler = new Handler();
    private Runnable autocloseRunnable;
    private boolean autocloseCancelled;

    private String url;
    private int articleId = -1;

    private Article article;

    private boolean archived;
    private boolean favorite;

    private boolean openPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyDialogTheme(this);
        super.onCreate(savedInstanceState);

        initialOpenTime = SystemClock.uptimeMillis();

        setContentView(R.layout.activity_edit_added_article);

        articleTitleTv = findViewById(R.id.editActivity_articleTitle);
        favoriteButton = findViewById(R.id.editActivity_favoriteButton);
        archiveButton = findViewById(R.id.editActivity_archiveButton);
        ImageButton tagButton = findViewById(R.id.editActivity_tagButton);
        openButton = findViewById(R.id.editActivity_openButton);

        favoriteButton.setOnClickListener(this::onFavoriteClick);
        archiveButton.setOnClickListener(this::onArchiveClick);
        tagButton.setOnClickListener(this::onTagClick);
        openButton.setOnClickListener(this::onOpenClick);

        if (savedInstanceState != null) {
            articleId = savedInstanceState.getInt(STATE_DISCOVERED_ARTICLE_ID, -1);
            archived = savedInstanceState.getBoolean(STATE_ARCHIVED, false);
            favorite = savedInstanceState.getBoolean(STATE_FAVORITE, false);
            openPending = savedInstanceState.getBoolean(STATE_OPEN_PENDING, false);
            autocloseCancelled = savedInstanceState.getBoolean(STATE_AUTOCLOSE_CANCELLED, false);
            initialOpenTime = savedInstanceState.getLong(STATE_INITIAL_OPEN_TIME, initialOpenTime);
        }

        url = getIntent().getStringExtra(PARAM_ARTICLE_URL);

        init();

        if (!autocloseCancelled) scheduleAutoclose();

        if (openPending) onOpenClick(null);

        EventHelper.register(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(STATE_DISCOVERED_ARTICLE_ID, articleId);
        outState.putBoolean(STATE_ARCHIVED, archived);
        outState.putBoolean(STATE_FAVORITE, favorite);
        outState.putBoolean(STATE_OPEN_PENDING, openPending);
        outState.putBoolean(STATE_AUTOCLOSE_CANCELLED, autocloseCancelled);
        outState.putLong(STATE_INITIAL_OPEN_TIME, initialOpenTime);
    }

    @Override
    protected void onDestroy() {
        EventHelper.unregister(this);

        cancelAutoclose(false);

        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onArticlesChangedEvent(ArticlesChangedEvent event) {
        Log.d(TAG, "onArticlesChangedEvent() started");

        if (articleId == -1) return;

        if (event.isChangedAny(articleId, CHANGE_SET_FOR_REINIT)) {
            init();

            openIfPending();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLocalArticleReplacedEvent(LocalArticleReplacedEvent event) {
        Log.d(TAG, "onLocalArticleReplacedEvent() started");

        if (TextUtils.equals(url, event.getGivenUrl())) {
            articleId = event.getArticleId();

            init();

            openIfPending();
        }
    }

    public void onArchiveClick(View view) {
        cancelAutoclose();

        archived = !archived;

        updateViews();

        OperationsHelper.archiveArticle(this, url, archived);
    }

    public void onFavoriteClick(View view) {
        cancelAutoclose();

        favorite = !favorite;

        updateViews();

        OperationsHelper.favoriteArticle(this, url, favorite);
    }

    public void onTagClick(View view) {
        cancelAutoclose();

        Intent intent = new Intent(this, ManageArticleTagsActivity.class);
        if (articleId != -1) {
            intent.putExtra(ManageArticleTagsActivity.PARAM_ARTICLE_ID, articleId);
        } else {
            intent.putExtra(ManageArticleTagsActivity.PARAM_ARTICLE_URL, url);
        }

        startActivity(intent);
    }

    public void onOpenClick(View view) {
        if (canOpen()) {
            openArticle();
        } else {
            openLater();
        }
    }

    private boolean canOpen() {
        return articleId != -1 && article != null;
    }

    private void openArticle() {
        Intent intent = new Intent(this, ReadArticleActivity.class);
        intent.putExtra(ReadArticleActivity.EXTRA_ID, article.getId());
        startActivity(intent);

        finish();
    }

    private void openLater() {
        cancelAutoclose();

        openPending = true;

        Drawable oldImage = openButton.getDrawable();
        CircularProgressDrawable progressDrawable = new CircularProgressDrawable(this) {
            @Override
            public int getIntrinsicWidth() {
                return oldImage.getIntrinsicWidth();
            }

            @Override
            public int getIntrinsicHeight() {
                return oldImage.getIntrinsicHeight();
            }
        };
        progressDrawable.setStyle(CircularProgressDrawable.DEFAULT);

        openButton.setImageDrawable(progressDrawable);
        progressDrawable.start();

        openButton.setEnabled(false);
        openButton.setClickable(false);
    }

    private void openIfPending() {
        if (openPending && canOpen()) openArticle();
    }

    private void init() {
        loadArticle();

        updateVars();
        updateViews();
    }

    private void loadArticle() {
        if (articleId != -1) {
            article = DbConnection.getSession().getArticleDao().queryBuilder()
                    .where(ArticleDao.Properties.ArticleId.eq(articleId))
                    .unique();
        } else {
            article = new OperationsWorker(this).findArticleByUrl(url);
        }

        if (articleId == -1 && article != null && article.getArticleId() != null) {
            articleId = article.getArticleId();
        }
    }

    private void updateVars() {
        if (article != null && article.getArticleId() != null) {
            archived = Boolean.TRUE.equals(article.getArchive());
            favorite = Boolean.TRUE.equals(article.getFavorite());
        }
    }

    private void updateViews() {
        String title = article != null && !TextUtils.isEmpty(article.getTitle())
                ? article.getTitle() : url;

        articleTitleTv.setText(title);

        favoriteButton.setImageResource(resolveResource(
                favorite ? R.attr.icon_favorite_undo : R.attr.icon_favorite));
        favoriteButton.setContentDescription(getString(
                favorite ? R.string.remove_from_favorites : R.string.add_to_favorites));

        archiveButton.setImageResource(resolveResource(
                archived ? R.attr.icon_read_undo : R.attr.icon_read));
        archiveButton.setContentDescription(getString(
                archived ? R.string.btnMarkUnread : R.string.btnMarkRead));
    }

    private int resolveResource(int resId) {
        Resources.Theme theme = getTheme();
        TypedValue value = new TypedValue();
        theme.resolveAttribute(resId, value, true);
        return value.resourceId;
    }

    private void scheduleAutoclose() {
        handler.postAtTime(autocloseRunnable = this::finish,
                initialOpenTime + AUTOCLOSE_DELAY);
    }

    private void cancelAutoclose() {
        cancelAutoclose(true);
    }

    private void cancelAutoclose(boolean byUser) {
        if (byUser) autocloseCancelled = true;

        handler.removeCallbacks(autocloseRunnable);
    }

}
