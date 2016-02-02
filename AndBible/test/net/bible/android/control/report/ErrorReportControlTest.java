package net.bible.android.control.report;

import static org.hamcrest.Matchers.equalTo;
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
		System.out.println(emailer.getText());
		assertThat(emailer.getText(), equalTo("yyy"));
		assertThat(emailer.getRecipient(), equalTo("zzz"));
	}

}
