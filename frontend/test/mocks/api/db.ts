import * as json from '~/domain/json';
import type {DomainObject, Id, IdKey} from '~/domain/objects';

/**
 * Represents a database table of domain objects.
 * A constraint can be enforced on the state of the rows, with the invariant that it must consider the empty table valid.
 */
class Table<T extends DomainObject> extends EventTarget {
  private rows: T[] = [];
  private nextId = 1;

  constructor(private isValid: (rows: T[]) => boolean) {
    super();
  }

  private tryChange(newRows: T[]) {
    if (!this.isValid(newRows)) return false;
    this.rows = newRows;
    this.dispatchEvent(new Event('change'));
    return true;
  }

  clear() {
    this.nextId = 1;
    this.tryChange([]);
  }

  findAll(predicate: (row: T) => boolean = () => true) {
    return this.rows.filter(predicate);
  }

  get({id}: T['id']) {
    return this.rows.find(row => row.id.id === id) ?? null;
  }

  update(object: T) {
    const index = this.rows.findIndex(row => row.id.id === object.id.id);
    if (index === -1) return false;
    const possible = [...this.rows];
    possible[index] = object;
    return this.tryChange(possible);
  }

  delete({id}: T['id']) {
    const index = this.rows.findIndex(row => row.id.id === id);
    const possible = [...this.rows];
    if (index === -1) return false;
    possible.splice(index, 1);
    return this.tryChange(possible);
  }

  insert(domainObject: T) {
    domainObject = {
      ...domainObject,
      id: {
        type: domainObject.id.type,
        id: this.nextId++,
      },
    };
    const possible = [...this.rows, domainObject];
    return this.tryChange(possible) ? domainObject.id : null;
  }
}

/**
 * Represents a database table of JSON representations of domain objects.
 * Very similar to table, but a JSONParserPair converts to/from JSON along the interface surface.
 */
export class JSONTable<T extends DomainObject> extends EventTarget {
  private table: Table<T>;

  constructor(
    private parserPair: json.JSONParserPair<T>,
    unique?: Array<keyof T>,
  ) {
    super();
    this.table = new Table(rows => {
      if (unique) {
        const keys = new Set(
          rows.map(row => JSON.stringify(unique.map(key => row[key]))),
        );
        return keys.size === rows.length;
      }

      return true;
    });
    this.table.addEventListener('change', () => {
      this.dispatchEvent(new Event('change'));
    });
  }

  clear() {
    this.table.clear();
  }

  findAll(predicate?: (row: T) => boolean) {
    return this.table.findAll(predicate).map(this.parserPair.unparse);
  }

  findMatching<
    K extends IdKey<T>,
    I extends string = T[K] extends Id<infer U> ? U : never,
  >(key: K, id: json.JSONDomainObject<DomainObject<I>>['id']) {
    return this.findAll(row => (row[key] as Id<I>).id === id);
  }

  get(id: json.JSONDomainObject<T>['id']) {
    const row = this.table.get(this.parserPair.field('id').parse(id));
    return row ? this.parserPair.unparse(row) : null;
  }

  update(object: json.JSONDomainObject<T>) {
    return this.table.update(this.parserPair.parse(object));
  }

  delete(id: json.JSONDomainObject<T>['id']) {
    return this.table.delete(this.parserPair.field('id').parse(id));
  }

  insert(domainObject: json.JSONDomainObject<T>) {
    const id = this.table.insert(this.parserPair.parse(domainObject));
    return id ? this.parserPair.field('id').unparse(id) : null;
  }
}
