import org.broadinstitute.dsde.firecloud.auth.AuthTokens
import org.broadinstitute.dsde.firecloud.pages.{WebBrowserSpec, WorkspaceDataPage}
import org.broadinstitute.dsde.firecloud.{CleanUp, Config, WebBrowserUtil}
import org.scalatest.selenium.WebBrowser
import org.scalatest.{FlatSpec, ParallelTestExecution, ShouldMatchers}

class DataTabSpec extends FlatSpec with WebBrowserSpec with ParallelTestExecution with ShouldMatchers with WebBrowser with WebBrowserUtil with CleanUp {

  behavior of "Data"

  it should "import a participants file" in withWebDriver { implicit driver =>
    val filename = "automation/src/test/resources/participants.txt"

    val billingProject = Config.Projects.default
    val wsName = "TestSpec_FireCloud_import_participants_file_" + randomUuid
    implicit val authToken = AuthTokens.testUser
    api.workspaces.create(billingProject, wsName)
    register cleanUp api.workspaces.delete(billingProject, wsName)

    val workspaceListPage = signIn(Config.Users.testUser.email, Config.Users.testUser.password)
    val workspaceDataTab = new WorkspaceDataPage(billingProject, wsName).open
    workspaceDataTab.importFile(filename)
    assert(workspaceDataTab.getNumberOfParticipants() == 1)

    //more checks should be added here
  }
}
