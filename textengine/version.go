package textengine

import "github.com/hashicorp/go-version"

const VersionName = "textengine"
const VersionFriendlyName = "Text Engine"
const VersionNumber = "0.0.1"

func VersionVersion() *version.Version {
	return version.Must(version.NewVersion(VersionNumber))
}
