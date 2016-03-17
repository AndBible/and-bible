package net.bible.android.control.report;

import net.bible.android.activity.BuildConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class)
public class ErrorReportControlTest {
	@Mock
	private EmailerStub emailer = new EmailerStub();

	private ErrorReportControl errorReportControl;

	@Before
	public void createErrorReportControl() throws Exception {
		errorReportControl = new ErrorReportControl(emailer);
	}

	@Test
	public void testSendErrorReportEmail() throws Exception {
		errorReportControl.sendErrorReportEmail(new Exception("Something happened"));
		assertThat(emailer.getEmailDialogTitle(), equalTo("Send Report"));
		assertThat(emailer.getSubject(), startsWith("Something happened:net.bible.android.control.report.ErrorReportControlTest.testSendErrorReportEmail:"));
		System.out.println(emailer.getText());
		assertThat(emailer.getText(), containsString("Something happened"));
		assertThat(emailer.getRecipient(), equalTo("errors.andbible@gmail.com"));
	}
}
