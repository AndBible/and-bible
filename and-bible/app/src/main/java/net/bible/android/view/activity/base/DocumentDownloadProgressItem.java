package net.bible.android.view.activity.base;

import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ProgressBar;

public class DocumentDownloadProgressItem {
		private int percentDone;
		private @Nullable ProgressBar progressBar;

		public synchronized void setPercentDone(int percentDone) {
			this.percentDone = percentDone;
		}

		public synchronized void setProgressBar(@Nullable ProgressBar progressBar) {
			this.progressBar = progressBar;
		}

		public synchronized void updateProgressBar() {
			updateProgressBar(percentDone);
		}

		private void updateProgressBar(final int percentDone) {
			if (progressBar!=null && progressBar.getParent()!=null) {
				progressBar.post(
						new Runnable() {
							@Override
							public void run() {
								progressBar.setProgress(percentDone);
								progressBar.setVisibility(percentDone>0? View.VISIBLE : View.GONE);
							}
						}
				);
			}
		}
	}
