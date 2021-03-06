package blended.updater.config

import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks

class ProfileSpec extends FreeSpec with PropertyChecks {

  import TestData._

  "Profile maps to SingleProfile" in {
    forAll { profile: ProfileGroup =>
      val singles = profile.toSingle
      assert(singles.size === profile.overlays.size)
      assert(ProfileGroup.fromSingleProfiles(singles) === List(profile))
    }
  }

  "SingleProfile maps to Profile" in {
    forAll { singles: Seq[Profile] =>
      val profiles = ProfileGroup.fromSingleProfiles(singles)
      assert(profiles.flatMap(_.toSingle).toSet === singles.toSet)
    }
  }

}
