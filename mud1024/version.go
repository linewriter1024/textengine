package mud1024

import "github.com/hashicorp/go-version"

const VersionName = "mud1024"
const VersionNumber = "0.0.1"

func VersionVersion() *version.Version {
	return version.Must(version.NewVersion(VersionNumber))
}
