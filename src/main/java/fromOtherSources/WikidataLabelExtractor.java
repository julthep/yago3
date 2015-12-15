package fromOtherSources;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javatools.administrative.Announce;
import javatools.datatypes.FinalSet;
import javatools.parsers.Char17;
import utils.Theme;
import basics.Fact;
import basics.FactComponent;
import basics.N4Reader;
import basics.RDFS;
import basics.YAGO;
import extractors.DataExtractor;
import fromThemes.TransitiveTypeExtractor;

/**
 * WikidataLabelExtractor - YAGO2s
 * 
 * Extracts labels from wikidata
 * 
 * @author Fabian
 * 
 */
public class WikidataLabelExtractor extends DataExtractor {

  public WikidataLabelExtractor(File wikidata) {
    super(wikidata);
  }

  public WikidataLabelExtractor() {
    this(new File("./data/wikidata.rdf"));
  }

  @Override
  public Set<Theme> input() {
    return new FinalSet<Theme>(TransitiveTypeExtractor.TRANSITIVETYPE, PatternHardExtractor.LANGUAGECODEMAPPING);
  }

  @Override
  public Set<Theme> inputCached() {
    return new FinalSet<Theme>(TransitiveTypeExtractor.TRANSITIVETYPE, PatternHardExtractor.LANGUAGECODEMAPPING);
  }

  /** Facts deduced from categories */
  public static final Theme WIKIPEDIALABELS = new Theme("wikipediaLabels", "Labels derived from the name of the instance in Wikipedia");

  /** Sources */
  public static final Theme WIKIPEDIALABELSOURCES = new Theme("wikipediaLabelSources", "Sources for the Wikipedia labels");

  /** Facts deduced from categories */
  public static final Theme WIKIDATAMULTILABELS = new Theme("wikidataMultiLabels", "Labels from Wikidata in multiple languages");

  /** Sources */
  public static final Theme WIKIDATAMULTILABELSOURCES = new Theme("wikidataMultiLabelSources", "Sources for the multilingual labels");

  @Override
  public Set<Theme> output() {
    return new FinalSet<Theme>(WIKIPEDIALABELSOURCES, WIKIPEDIALABELS, WIKIDATAMULTILABELSOURCES, WIKIDATAMULTILABELS);
  }

  @Override
  public void extract() throws Exception {
    Map<String, String> languagemap = PatternHardExtractor.LANGUAGECODEMAPPING.factCollection().getStringMap("<hasThreeLetterLanguageCode>");
    Set<String> entities = TransitiveTypeExtractor.TRANSITIVETYPE.factCollection().getSubjects();

    Announce.message("Loaded", languagemap.size(), "languages and ", entities.size(), "entities");

    // first write the English names
    for (String yagoEntity : entities) {
      write(WIKIPEDIALABELS, new Fact(yagoEntity, YAGO.hasPreferredName, FactComponent.forStringWithLanguage(preferredName(yagoEntity), "eng")),
          WIKIPEDIALABELSOURCES, "<http://wikidata.org>", "WikidataLabelExtractor");
      for (String name : trivialNamesOf(yagoEntity)) {
        write(WIKIPEDIALABELS, new Fact(yagoEntity, RDFS.label, FactComponent.forStringWithLanguage(name, "eng")), WIKIPEDIALABELSOURCES,
            "<http://wikidata.org>", "WikidataLabelExtractor");
      }

    }

    // Now write the foreign names
    N4Reader nr = new N4Reader(inputData);
    // Maps a language such as "en" to the name in that language
    Map<String, String> language2name = new HashMap<String, String>();
    while (nr.hasNext()) {
      Fact f = nr.next();
      // Record a new name in the map
      if (f.getRelation().endsWith("/inLanguage>")) {
        String lan = FactComponent.stripQuotes(f.getObject());
        String nam = FactComponent.stripWikipediaPrefix(Char17.decodePercentage(f.getSubject()));
        if (nam != null) language2name.put(lan, nam);
      } else if (f.getArg(2).endsWith("#Item>") && !language2name.isEmpty()) {
        // New item starts, let's flush out the previous one
        String mostEnglishLan = DictionaryExtractor.mostEnglishLanguage(language2name.keySet());
        if (mostEnglishLan != null) {
          String mostEnglishName = language2name.get(mostEnglishLan);
          String yagoEntity = FactComponent.forForeignYagoEntity(mostEnglishName, mostEnglishLan);
          if (entities.contains(yagoEntity)) {
            for (String lan : language2name.keySet()) {
              String foreignName = language2name.get(lan);
              if (lan.length() == 2) lan = languagemap.get(lan);
              if (lan == null || lan.length() != 3) continue;
              for (String name : trivialNamesOf(foreignName)) {
                write(WIKIDATAMULTILABELS, new Fact(yagoEntity, RDFS.label, FactComponent.forStringWithLanguage(name, lan)),
                    WIKIDATAMULTILABELSOURCES, "<http://wikidata.org>", "WikidataLabelExtractor");
              }
            }
          }

        }
        language2name.clear();
      }
    }
    nr.close();
  }

  /** returns the (trivial) names of an entity */
  public static Set<String> trivialNamesOf(String titleEntity) {
    Set<String> result = new TreeSet<>();
    String name = preferredName(titleEntity);
    result.add(name);
    String norm = Char17.normalize(name);
    if (!norm.contains("[?]")) result.add(norm);
    if (name.contains(" (")) {
      result.add(name.substring(0, name.indexOf(" (")).trim());
    }
    if (name.contains(",") && !name.contains("(")) {
      result.add(name.substring(0, name.indexOf(",")).trim());
    }
    return (result);
  }

  /** returns the preferred name */
  public static String preferredName(String titleEntity) {
    return (Char17.decode(FactComponent.stripBracketsAndLanguage(titleEntity).replace('_', ' ')));
  }

  public static void main(String[] args) throws Exception {
    new WikidataLabelExtractor().extract(new File("c:/fabian/data/yago3"), "test");
  }
}