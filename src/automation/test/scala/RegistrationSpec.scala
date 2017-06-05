import org.broadinstitute.dsde.firecloud.Config
import org.broadinstitute.dsde.firecloud.pages.{DataLibraryPage, RegistrationPage, SignInPage, WebBrowserSpec}
import org.scalatest.FlatSpec

/**
  * Tests for new user registration scenarios.
  */
class RegistrationSpec extends FlatSpec with WebBrowserSpec {

  behavior of "FireCloud registration page"

  it should "allow sign in of registered user" in withWebDriver { implicit driver =>
    new SignInPage(Config.FireCloud.baseUrl).open.signIn(Config.Accounts.testUserEmail, Config.Accounts.testUserPassword)
  }

  it should "allow a user to register" in withWebDriver { implicit driver =>
    new SignInPage(Config.FireCloud.baseUrl).open.signIn("testreg.firec@gmail.com", "")

    new RegistrationPage().register(firstName = "Test", lastName = "Dummy", title = "Tester",
      contactEmail = "test@firecloud.org", institute = "Broad", institutionalProgram = "DSDE",
      nonProfitStatus = true, principalInvestigator = "Nobody", city = "Cambridge",
      state = "MA", country = "USA")

    new DataLibraryPage().validateLocation()
  }
}