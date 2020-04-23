REPLACE=$(mvn -q -DforceStdout help:evaluate -Dexpression=kokoro.preinstallMavenDependencies | grep -i true);
if [[ $? -eq 0 ]]; then
    echo -e "\n Preinstalling Maven dependencies...\n"
    mvn -q -batch-mode --fail-never clean verify \
      -DskipTests \
      -Dfindbugs.skip=true
    echo -e "\n Maven preinstall complete\nt"
fi
