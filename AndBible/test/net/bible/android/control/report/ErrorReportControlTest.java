package net.bible.android.control.report;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ErrorReportControlTest {
	@Mock
	private EmailerStub emailer = new EmailerStub();
	private ErrorReportControl errorReportControl;

	@Before
	public void createErrorReportControl() throws Exception {
		errorReportControl = new ErrorReportControl(emailer);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSendErrorReportEmail() throws Exception {
		errorReportControl.sendErrorReportEmail(new Exception("Something happened"));
		assertThat(emailer.getEmailDialogTitle(), equalTo("Report"));
		assertThat(emailer.getSubject(), startsWith("Something happened:net.bible.android.control.report.ErrorReportControlTest.testSendErrorReportEmail:"));
		System.out.println(emailer.getText());
		assertThat(emailer.getText(), containsString("Something happened"));
		assertThat(emailer.getRecipient(), equalTo("errors.andbible@gmail.com"));
	}
}
