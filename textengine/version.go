package textengine

import "github.com/hashicorp/go-version"

const VersionName = "textengine"
const VersionFriendlyName = "Text Engine"
const VersionNumber = "0.0.1"
const VersionURL = "https://benleskey.com/aka/textengine"

func VersionVersion() *version.Version {
	return version.Must(version.NewVersion(VersionNumber))
}
